package io.github.dulle88.atrium.portal

import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

class PortalServer internal constructor(
    port: Int,
    private val backend: PortalBackend,
    private val indexHtml: () -> String,
    private val monotonicMs: () -> Long
) : NanoHTTPD(port) {
    constructor(port: Int, backend: PortalBackend, indexHtml: () -> String) :
        this(port, backend, indexHtml, { System.nanoTime() / 1_000_000 })

    private val gson = Gson()

    private class NoticeBody(val text: String?, val fromMs: Long?, val untilMs: Long?)
    private class OrderBody(val ids: List<String>?)
    private class SecondsBody(val seconds: Int?)
    private class ClockBody(val epochMs: Long?)
    private class PinBody(val pin: String?)
    private class PhonesBody(val contacts: List<PhoneContact?>?)

    private object PayloadTooLarge : Response.IStatus {
        override fun getRequestStatus() = 413
        override fun getDescription() = "413 Payload Too Large"
    }

    private object TooManyRequests : Response.IStatus {
        override fun getRequestStatus() = 429
        override fun getDescription() = "429 Too Many Requests"
    }

    private object InsufficientStorage : Response.IStatus {
        override fun getRequestStatus() = 507
        override fun getDescription() = "507 Insufficient Storage"
    }

    private val lock = Any()
    private var failedAttempts = 0
    private var lockedUntilMs = 0L

    private sealed interface PinCheck {
        object Ok : PinCheck
        object Wrong : PinCheck
        class Locked(val seconds: Long) : PinCheck
    }

    /** Lock check, comparison and failure bookkeeping happen atomically. */
    private fun checkPin(supplied: String?, expected: String): PinCheck = synchronized(lock) {
        val remaining = lockedUntilMs - monotonicMs()
        if (remaining > 0) return@synchronized PinCheck.Locked((remaining + 999) / 1000)
        val correct = supplied != null &&
            MessageDigest.isEqual(supplied.toByteArray(), expected.toByteArray())
        if (correct) {
            failedAttempts = 0
            return@synchronized PinCheck.Ok
        }
        failedAttempts++
        if (failedAttempts % FAILS_PER_LOCK == 0) {
            val streak = failedAttempts / FAILS_PER_LOCK
            val lockMs = minOf(BASE_LOCK_MS shl minOf(streak - 1, 10), MAX_LOCK_MS)
            lockedUntilMs = monotonicMs() + lockMs
        }
        PinCheck.Wrong
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        if (session.method == Method.GET && (uri == "/" || uri == "/index.html")) {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", indexHtml())
        }
        if (!uri.startsWith("/api/")) return fail(Response.Status.NOT_FOUND, "Not found")
        val expected = runBlocking { backend.pin() }
        // Refusals below leave the request body unread, so the connection is closed: on keep-alive the
        // leftover bytes would otherwise be parsed as the next request.
        when (val check = checkPin(session.headers["x-pin"], expected)) {
            is PinCheck.Locked ->
                return refuse(fail(TooManyRequests, "Too many wrong PINs. Try again in ${check.seconds} seconds."))
            PinCheck.Wrong -> return refuse(fail(Response.Status.UNAUTHORIZED, "Wrong PIN"))
            PinCheck.Ok -> Unit
        }
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val cap = if (session.method == Method.POST && isPhotoUpload(uri)) MAX_PHOTO_REQUEST_BYTES else MAX_JSON_BYTES
        if (session.method == Method.POST && length > cap) {
            return refuse(fail(PayloadTooLarge, "Request body is too large"))
        }
        return try {
            route(session, uri)
        } catch (e: ResponseException) {
            fail(e.status, "Bad request")
        } catch (e: PortalRejection) {
            fail(statusFor(e.httpStatus), e.message ?: "Request refused")
        } catch (e: Exception) {
            System.err.println("PortalServer error: $e")
            fail(Response.Status.INTERNAL_ERROR, "Server error")
        }
    }

    private fun isPhotoUpload(uri: String) = uri == "/api/photos" || (uri.startsWith("/api/notices/") && uri.endsWith("/photos"))

    private fun route(session: IHTTPSession, uri: String): Response {
        val m = session.method
        return when {
            m == Method.GET && uri == "/api/status" -> json(Response.Status.OK, runBlocking { backend.status() })
            m == Method.POST && uri == "/api/clock" -> postClock(session)
            m == Method.POST && uri == "/api/pin" -> postPin(session)
            m == Method.POST && uri == "/api/phones" -> postPhones(session)
            m == Method.GET && uri.startsWith("/api/photo/") -> getPhoto(session, uri.removePrefix("/api/photo/"))
            m == Method.POST && uri == "/api/photos" -> receivePhoto(session) { name, tmp ->
                json(Response.Status.OK, mapOf("id" to runBlocking { backend.addPhoto(name, tmp) }))
            }
            m == Method.DELETE && uri.startsWith("/api/photos/") -> {
                val id = uri.removePrefix("/api/photos/")
                when {
                    !PHOTO_ID.matches(id) -> fail(Response.Status.BAD_REQUEST, "Invalid photo id")
                    runBlocking { backend.deletePhoto(id) } -> ok()
                    else -> fail(Response.Status.NOT_FOUND, "Photo not found")
                }
            }
            m == Method.POST && uri == "/api/slideshow/order" -> postOrder(session)
            m == Method.POST && uri == "/api/slideshow/seconds" -> postSeconds(session)
            m == Method.POST && uri == "/api/notices" -> postNotice(session)
            uri.startsWith("/api/notices/") -> noticeRoute(session, uri.removePrefix("/api/notices/"))
            else -> fail(Response.Status.NOT_FOUND, "Unknown endpoint")
        }
    }

    /** [rest] is `<id>`, `<id>/photos` or `<id>/publish`; the id is validated before anything else. */
    private fun noticeRoute(session: IHTTPSession, rest: String): Response {
        val id = rest.substringBefore('/')
        if (!NOTICE_ID.matches(id)) return fail(Response.Status.BAD_REQUEST, "Invalid notice id")
        val action = rest.substringAfter('/', "")
        return when {
            session.method == Method.DELETE && action.isEmpty() ->
                if (runBlocking { backend.deleteNotice(id) }) ok() else fail(Response.Status.NOT_FOUND, "Notice not found")
            session.method == Method.POST && action == "photos" -> receivePhoto(session) { name, tmp ->
                json(Response.Status.OK, mapOf("id" to runBlocking { backend.addNoticePhoto(id, name, tmp) }))
            }
            session.method == Method.POST && action == "publish" -> {
                runBlocking { backend.publishNotice(id) }
                ok()
            }
            else -> fail(Response.Status.NOT_FOUND, "Unknown endpoint")
        }
    }

    private fun getPhoto(session: IHTTPSession, id: String): Response {
        if (!PHOTO_ID.matches(id)) return fail(Response.Status.BAD_REQUEST, "Invalid photo id")
        val thumb = session.parameters["thumb"]?.firstOrNull() == "1"
        val photo = runBlocking { backend.photo(id, thumb) } ?: return fail(Response.Status.NOT_FOUND, "Photo not found")
        val response = newFixedLengthResponse(Response.Status.OK, photo.mime, FileInputStream(photo.file), photo.file.length())
        response.addHeader("Cache-Control", "private, max-age=86400")
        return response
    }

    /**
     * The JSON body as UTF-8. NanoHTTPD's own parsing decodes a body without a charset as US-ASCII,
     * which garbles Serbian letters, so the bytes are read here (the size was capped in [serve]).
     */
    private fun bodyOf(session: IHTTPSession): String? {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return null
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = session.inputStream.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun postNotice(session: IHTTPSession): Response {
        val body = runCatching { gson.fromJson(bodyOf(session), NoticeBody::class.java) }.getOrNull()
            ?: return fail(Response.Status.BAD_REQUEST, "Bad request")
        val text = body.text?.trim()
        if (text != null && text.length > MAX_TEXT) return fail(Response.Status.BAD_REQUEST, "Message is longer than $MAX_TEXT characters")
        val until = body.untilMs
        if (until == null || until <= 0 || (body.fromMs != null && body.fromMs <= 0)) {
            return fail(Response.Status.BAD_REQUEST, "Invalid time")
        }
        val id = runBlocking { backend.createNotice(text, body.fromMs, until) }
        return json(Response.Status.OK, mapOf("id" to id))
    }

    private fun postOrder(session: IHTTPSession): Response {
        val ids = runCatching { gson.fromJson(bodyOf(session), OrderBody::class.java) }.getOrNull()?.ids
        if (ids == null || ids.size > MAX_ORDER_IDS || ids.any { it == null || !PHOTO_ID.matches(it) }) {
            return fail(Response.Status.BAD_REQUEST, "Invalid order")
        }
        runBlocking { backend.setSlideshowOrder(ids) }
        return ok()
    }

    private fun postSeconds(session: IHTTPSession): Response {
        val seconds = runCatching { gson.fromJson(bodyOf(session), SecondsBody::class.java) }.getOrNull()?.seconds
            ?: return fail(Response.Status.BAD_REQUEST, "Invalid duration")
        runBlocking { backend.setSlideshowSeconds(seconds) }
        return ok()
    }

    private fun postPin(session: IHTTPSession): Response {
        val pin = runCatching { gson.fromJson(bodyOf(session), PinBody::class.java) }.getOrNull()?.pin
        if (pin == null || pin.length != 4 || !pin.all { it in '0'..'9' }) {
            return fail(Response.Status.BAD_REQUEST, "The PIN must be exactly 4 digits")
        }
        runBlocking { backend.setPin(pin) }
        return ok()
    }

    /** Replaces the whole contact list; [PhoneContacts.validate] refuses a bad one with 400 and a fixed message. */
    private fun postPhones(session: IHTTPSession): Response {
        val contacts = runCatching { gson.fromJson(bodyOf(session), PhonesBody::class.java) }.getOrNull()?.contacts
        val clean = PhoneContacts.validate(contacts)
        runBlocking { backend.setPhones(clean) }
        return ok()
    }

    private fun postClock(session: IHTTPSession): Response {
        val raw = bodyOf(session)
        val ms = runCatching { gson.fromJson(raw, ClockBody::class.java) }.getOrNull()?.epochMs
        if (ms == null || ms < MIN_EPOCH || ms > MAX_EPOCH) return fail(Response.Status.BAD_REQUEST, "Invalid time")
        runBlocking { backend.setTime(ms) }
        return ok()
    }

    /** Parses a one-file multipart upload, checks name, type and size, then hands it to [use]. */
    private fun receivePhoto(session: IHTTPSession, use: (String, File) -> Response): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tmp = files["photo"]?.let(::File) ?: return fail(Response.Status.BAD_REQUEST, "No photo received")
        val name = (session.parameters["photo"]?.firstOrNull().orEmpty())
            .substringAfterLast('/').substringAfterLast('\\')
            .filterNot { it.isISOControl() }.trim()
        if (name.isEmpty()) return fail(Response.Status.BAD_REQUEST, "Missing file name")
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_EXTENSIONS || !matchesMagic(tmp, ext)) {
            return fail(Response.Status.BAD_REQUEST, "Only JPEG and PNG photos are accepted")
        }
        if (tmp.length() > MAX_PHOTO_BYTES) return fail(Response.Status.BAD_REQUEST, "Photo is larger than 15 MB")
        return use(name, tmp)
    }

    private fun matchesMagic(file: File, ext: String): Boolean {
        val head = ByteArray(8)
        val read = try {
            file.inputStream().use { it.read(head) }
        } catch (_: IOException) {
            return false
        }
        val magic = if (ext == "png") PNG_MAGIC else JPEG_MAGIC
        return read >= magic.size && magic.indices.all { head[it] == magic[it] }
    }

    private fun statusFor(code: Int): Response.IStatus = when (code) {
        507 -> InsufficientStorage
        else -> Response.Status.lookup(code) ?: Response.Status.BAD_REQUEST
    }

    private fun refuse(response: Response) = response.apply { closeConnection(true) }

    private fun ok() = json(Response.Status.OK, mapOf("ok" to true))

    private fun fail(status: Response.IStatus, message: String) = json(status, mapOf("error" to message))

    private fun json(status: Response.IStatus, body: Any): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", gson.toJson(body))

    companion object {
        private const val MAX_TEXT = 2000
        private const val MAX_JSON_BYTES = 64L * 1024
        private const val MAX_PHOTO_REQUEST_BYTES = 16L * 1024 * 1024
        private const val FAILS_PER_LOCK = 5
        private const val BASE_LOCK_MS = 30_000L
        private const val MAX_LOCK_MS = 300_000L
        private const val MAX_ORDER_IDS = 1000
        /** Web photos (`web-<uuid>`), legacy synced items and notice photos (`n-<notice>-<index>`). */
        private val PHOTO_ID = Regex("^[A-Za-z0-9_-]{1,100}$")
        private val NOTICE_ID = Regex("^[A-Za-z0-9]{1,32}$")
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private const val MAX_PHOTO_BYTES = 15L * 1024 * 1024
        private const val MIN_EPOCH = 1_577_836_800_000L // 2020-01-01
        private const val MAX_EPOCH = 4_102_444_800_000L // 2100-01-01
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")

        fun startOnFreePort(backend: PortalBackend, indexHtml: () -> String): PortalServer? {
            for (port in 8080..8090) {
                val server = PortalServer(port, backend, indexHtml)
                try {
                    server.start(SOCKET_READ_TIMEOUT, true)
                    return server
                } catch (_: IOException) {
                    // port busy, try next
                }
            }
            return null
        }
    }
}
