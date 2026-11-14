package io.github.dulle88.atrium.portal

import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private class FakeBackend : PortalBackend {
    var time: Long? = null
    val photos = mutableListOf<PhotoInfo>()
    val notices = mutableMapOf<String, NoticeBody>()
    val noticePhotos = mutableMapOf<String, MutableList<String>>()
    val published = mutableSetOf<String>()
    var order: List<String>? = null
    var seconds = 10
    val photoFile: File = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
    var lastThumb: Boolean? = null
    @Volatile var pinDelayMs = 0L
    @Volatile var currentPin = "1234"
    @Volatile var storageFull = false
    var phones: List<PhoneContact> = PhoneContacts.DEFAULTS

    data class NoticeBody(val text: String?, val fromMs: Long?, val untilMs: Long)

    override suspend fun pin(): String { if (pinDelayMs > 0) Thread.sleep(pinDelayMs); return currentPin }
    override suspend fun setPin(newPin: String) { currentPin = newPin }
    override suspend fun status() = PortalStatus(
        42L, "06:51", "16:32", SlideshowInfo(seconds, photos.toList()),
        listOf(NoticeInfo("abc", "Lift <b>", listOf(PhotoInfo("n-abc-0", "x.jpg")), 1L, 99L, "active")),
        TvInfo("notice", 99L, null),
        phones
    )
    override suspend fun setTime(epochMs: Long) { time = epochMs }
    override suspend fun photo(id: String, thumb: Boolean): PhotoFile? {
        lastThumb = thumb
        return if (id == "web-1" || id == "n-abc-0") PhotoFile(photoFile, "image/jpeg") else null
    }
    override suspend fun addPhoto(filename: String, source: File): String {
        if (storageFull) throw StorageFullException()
        val id = "web-${photos.size}"
        photos += PhotoInfo(id, filename)
        return id
    }
    override suspend fun deletePhoto(id: String) = photos.removeIf { it.id == id }
    override suspend fun setSlideshowOrder(ids: List<String>) { order = ids }
    override suspend fun setSlideshowSeconds(seconds: Int) {
        if (seconds !in PortalStore.SLIDESHOW_SECONDS) throw InvalidRequestException("Invalid duration")
        this.seconds = seconds
    }
    override suspend fun createNotice(text: String?, fromMs: Long?, untilMs: Long): String {
        if (untilMs < 1000) throw InvalidRequestException("The end time has already passed")
        val id = "n${notices.size}"
        notices[id] = NoticeBody(text, fromMs, untilMs)
        return id
    }
    override suspend fun addNoticePhoto(noticeId: String, filename: String, source: File): String {
        if (storageFull) throw StorageFullException()
        val list = noticePhotos.getOrPut(noticeId) { if (noticeId in notices) mutableListOf() else throw NoticeNotFoundException() }
        list += filename
        return "n-$noticeId-${list.size - 1}"
    }
    override suspend fun publishNotice(noticeId: String) {
        if (noticeId !in notices) throw NoticeNotFoundException()
        if (notices[noticeId]?.text == null && noticePhotos[noticeId].isNullOrEmpty()) {
            throw InvalidRequestException("Add a message or at least one photo first")
        }
        published += noticeId
    }
    override suspend fun deleteNotice(noticeId: String) = notices.remove(noticeId) != null
    override suspend fun setPhones(contacts: List<PhoneContact>) { phones = contacts }
}

class PortalServerTest {
    private val backend = FakeBackend()
    private lateinit var server: PortalServer
    private val base get() = "http://127.0.0.1:${server.listeningPort}"

    private var now = 1_000_000L

    @Before fun start() {
        server = PortalServer(0, backend, { "<html>ok</html>" }, { now })
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
    }

    @After fun stop() = server.stop()

    private fun call(method: String, path: String, body: String? = null, pin: String? = "1234",
                     contentType: String = "application/json", charset: java.nio.charset.Charset = Charsets.ISO_8859_1): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        if (pin != null) c.setRequestProperty("X-Pin", pin)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.outputStream.use { it.write(body.toByteArray(charset)) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to text
    }

    @Test fun servesIndexWithoutPin() {
        val (code, body) = call("GET", "/", pin = null)
        assertEquals(200, code)
        assertTrue(body.contains("ok"))
    }

    @Test fun apiRejectsMissingOrWrongPin() {
        assertEquals(401, call("GET", "/api/status", pin = null).first)
        assertEquals(401, call("GET", "/api/status", pin = "0000").first)
    }

    @Test fun statusReturnsBackendState() {
        val (code, body) = call("GET", "/api/status")
        assertEquals(200, code)
        assertTrue(body.contains("\"nowMs\":42"))
    }

    @Test fun changesPin() {
        assertEquals(200, call("POST", "/api/pin", """{"pin":"0901"}""").first)
        assertEquals("0901", backend.currentPin)
        // the old PIN stops working, the new one works
        assertEquals(401, call("GET", "/api/status", pin = "1234").first)
        assertEquals(200, call("GET", "/api/status", pin = "0901").first)
    }

    @Test fun rejectsInvalidNewPin() {
        for (bad in listOf("""{"pin":"12"}""", """{"pin":"12345"}""", """{"pin":"abcd"}""", """{"pin":""}""", """{}""")) {
            assertEquals(bad, 400, call("POST", "/api/pin", bad).first)
        }
        assertEquals("1234", backend.currentPin)
    }

    @Test fun changingPinNeedsCurrentPin() {
        assertEquals(401, call("POST", "/api/pin", """{"pin":"0901"}""", pin = "0000").first)
        assertEquals("1234", backend.currentPin)
    }

    @Test fun setsClock() {
        assertEquals(200, call("POST", "/api/clock", """{"epochMs":1790000000000}""").first)
        assertEquals(1_790_000_000_000L, backend.time)
        assertEquals(400, call("POST", "/api/clock", """{"epochMs":5}""").first)
    }

    private val jpegMagic = "\u00FF\u00D8\u00FF\u00E0DATA"
    private val pngMagic = "\u0089PNG\r\n\u001A\nDATA"

    private fun multipart(name: String, content: String, path: String = "/api/photos"): Pair<Int, String> {
        val boundary = "XBOUNDARYX"
        val body = "--$boundary\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"$name\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n$content\r\n--$boundary--\r\n"
        return call("POST", path, body, contentType = "multipart/form-data; boundary=$boundary")
    }

    @Test fun uploadsJpegAndRejectsOtherTypes() {
        assertEquals(200, multipart("lobby.jpg", jpegMagic).first)
        assertEquals("lobby.jpg", backend.photos.single().filename)
        assertEquals(400, multipart("script.exe", jpegMagic).first)
    }

    @Test fun rejectsNonImageContentAndMismatchedFamily() {
        assertEquals(400, multipart("fake.jpg", "just text").first)
        assertEquals(400, multipart("fake.png", jpegMagic).first)
        assertEquals(400, multipart("fake.jpg", pngMagic).first)
        assertEquals(200, multipart("ok.png", pngMagic).first)
        assertEquals(1, backend.photos.size)
    }

    @Test fun sanitizesUploadFilename() {
        assertEquals(200, multipart("../../evil.jpg", jpegMagic).first)
        assertEquals("evil.jpg", backend.photos.single().filename)
    }

    @Test fun rejectsBadPhotoIds() {
        assertEquals(400, call("DELETE", "/api/photos/..%2F..%2Fx").first)
        assertEquals(400, call("DELETE", "/api/photos/a.b").first)
        assertEquals(400, call("DELETE", "/api/photos/a%20b").first)
        assertEquals(400, call("DELETE", "/api/photos/" + "a".repeat(101)).first)
        assertEquals(404, call("DELETE", "/api/photos/legacy_item-1").first)
    }

    @Test fun rejectsOversizedJsonBody() {
        val big = "a".repeat(70 * 1024)
        assertEquals(413, call("POST", "/api/notices", """{"text":"$big","untilMs":5000}""").first)
    }

    @Test fun locksOutAfterFiveWrongPins() {
        repeat(5) { assertEquals(401, call("GET", "/api/status", pin = "0000").first) }
        val (code, body) = call("GET", "/api/status")
        assertEquals(429, code)
        assertTrue(body.contains("Too many wrong PINs. Try again in"))
        now += 31_000
        assertEquals(200, call("GET", "/api/status").first)
    }

    @Test fun parallelWrongPinsCannotExceedLockoutBudget() {
        backend.pinDelayMs = 20
        val pool = java.util.concurrent.Executors.newFixedThreadPool(50)
        try {
            val results = (1..50).map { pool.submit<Int> { call("GET", "/api/status", pin = "0000").first } }
                .map { it.get() }
            assertTrue("401 count was ${results.count { it == 401 }}", results.count { it == 401 } <= 5)
            assertTrue(results.all { it == 401 || it == 429 })
        } finally {
            pool.shutdown()
        }
    }

    @Test fun correctPinResetsFailureCounter() {
        repeat(4) { call("GET", "/api/status", pin = "0000") }
        assertEquals(200, call("GET", "/api/status").first)
        repeat(4) { call("GET", "/api/status", pin = "0000") }
        assertEquals(200, call("GET", "/api/status").first)
    }

    @Test fun lockoutDoublesOnNextStreak() {
        repeat(5) { call("GET", "/api/status", pin = "0000") }
        now += 31_000
        repeat(5) { call("GET", "/api/status", pin = "0000") }
        now += 31_000
        assertEquals(429, call("GET", "/api/status").first)
        now += 31_000
        assertEquals(200, call("GET", "/api/status").first)
    }

    @Test fun deletesPhoto() {
        backend.photos += PhotoInfo("web-9", "a.jpg")
        assertEquals(200, call("DELETE", "/api/photos/web-9").first)
        assertEquals(404, call("DELETE", "/api/photos/web-9").first)
    }

    @Test fun uploadReturns507WhenStorageIsFull() {
        backend.storageFull = true
        val (code, body) = multipart("lobby.jpg", jpegMagic)
        assertEquals(507, code)
        assertEquals("""{"error":"Not enough storage on the TV. Delete some photos first."}""", body)
        assertTrue(backend.photos.isEmpty())
    }

    // --- Status and photos ---

    @Test fun statusHasSlideshowNoticesAndTvMode() {
        backend.photos += PhotoInfo("web-1", "a.jpg")
        val (code, body) = call("GET", "/api/status")
        assertEquals(200, code)
        assertTrue(body, body.contains("\"slideshow\":{\"seconds\":10,\"photos\":[{\"id\":\"web-1\",\"filename\":\"a.jpg\"}]}"))
        assertTrue(body, body.contains("\"state\":\"active\""))
        assertTrue(body, body.contains("\"tv\":{\"mode\":\"notice\",\"untilMs\":99}"))
    }

    private fun raw(path: String, pin: String? = "1234"): Triple<Int, String?, ByteArray> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        if (pin != null) c.setRequestProperty("X-Pin", pin)
        val code = c.responseCode
        val bytes = (if (code < 400) c.inputStream else c.errorStream).readBytes()
        return Triple(code, c.contentType, bytes)
    }

    @Test fun servesPhotoBytesAndThumbs() {
        val (code, type, bytes) = raw("/api/photo/web-1")
        assertEquals(200, code)
        assertEquals("image/jpeg", type)
        assertEquals(listOf<Byte>(1, 2, 3), bytes.toList())
        assertEquals(false, backend.lastThumb)
        assertEquals(200, raw("/api/photo/n-abc-0?thumb=1").first)
        assertEquals(true, backend.lastThumb)
    }

    @Test fun photoNeedsPinAndAValidKnownId() {
        assertEquals(401, raw("/api/photo/web-1", pin = null).first)
        assertEquals(400, raw("/api/photo/..%2Fsecret").first)
        assertEquals(404, raw("/api/photo/web-404").first)
    }

    // --- Slideshow ---

    @Test fun savesSlideshowOrder() {
        assertEquals(200, call("POST", "/api/slideshow/order", """{"ids":["web-2","web-1"]}""").first)
        assertEquals(listOf("web-2", "web-1"), backend.order)
    }

    @Test fun rejectsBadSlideshowOrder() {
        assertEquals(400, call("POST", "/api/slideshow/order", """{"ids":["../x"]}""").first)
        assertEquals(400, call("POST", "/api/slideshow/order", """{"nope":1}""").first)
        assertEquals(400, call("POST", "/api/slideshow/order", "not json").first)
        assertEquals(null, backend.order)
    }

    @Test fun savesSlideshowSecondsFromTheAllowedSet() {
        assertEquals(200, call("POST", "/api/slideshow/seconds", """{"seconds":30}""").first)
        assertEquals(30, backend.seconds)
        val (code, body) = call("POST", "/api/slideshow/seconds", """{"seconds":7}""")
        assertEquals(400, code)
        assertEquals("""{"error":"Invalid duration"}""", body)
        assertEquals(400, call("POST", "/api/slideshow/seconds", "{}").first)
        assertEquals(30, backend.seconds)
    }

    // --- Notices ---

    @Test fun createsAttachesPublishesAndDeletesANotice() {
        val (code, body) = call("POST", "/api/notices", """{"text":" Lift ","fromMs":2000,"untilMs":5000}""")
        assertEquals(200, code)
        assertEquals("""{"id":"n0"}""", body)
        assertEquals(FakeBackend.NoticeBody("Lift", 2000, 5000), backend.notices["n0"])
        val photo = multipart("p.jpg", jpegMagic, "/api/notices/n0/photos")
        assertEquals(200, photo.first)
        assertEquals("""{"id":"n-n0-0"}""", photo.second)
        assertEquals(200, call("POST", "/api/notices/n0/publish").first)
        assertTrue("n0" in backend.published)
        assertEquals(200, call("DELETE", "/api/notices/n0").first)
        assertEquals(404, call("DELETE", "/api/notices/n0").first)
    }

    @Test fun jsonBodiesAreUtf8EvenWithoutACharset() {
        val text = "Sastanak večeras, ćao đaci! Ж"
        assertEquals(200, call("POST", "/api/notices", """{"text":"$text","untilMs":5000}""", charset = Charsets.UTF_8).first)
        assertEquals(text, backend.notices["n0"]?.text)
    }

    @Test fun rejectsBadNotices() {
        assertEquals(400, call("POST", "/api/notices", """{"text":"x"}""").first)
        assertEquals(400, call("POST", "/api/notices", """{"text":"x","untilMs":-1}""").first)
        assertEquals(400, call("POST", "/api/notices", """{"text":"x","fromMs":0,"untilMs":5000}""").first)
        assertEquals(400, call("POST", "/api/notices", "garbage").first)
        val huge = "a".repeat(2001)
        assertEquals(400, call("POST", "/api/notices", """{"text":"$huge","untilMs":5000}""").first)
        val (code, body) = call("POST", "/api/notices", """{"text":"x","untilMs":500}""")
        assertEquals(400, code)
        assertEquals("""{"error":"The end time has already passed"}""", body)
        assertTrue(backend.notices.isEmpty())
    }

    @Test fun rejectsBadNoticeIdsAndUnknownNotices() {
        assertEquals(400, call("DELETE", "/api/notices/a-b").first)
        assertEquals(400, call("POST", "/api/notices/..%2Fx/publish").first)
        assertEquals(404, call("POST", "/api/notices/zzz/publish").first)
        assertEquals(404, multipart("p.jpg", jpegMagic, "/api/notices/zzz/photos").first)
        assertEquals(404, call("POST", "/api/notices/zzz/other").first)
    }

    @Test fun publishWithoutContentIsRefused() {
        call("POST", "/api/notices", """{"untilMs":5000}""")
        val (code, body) = call("POST", "/api/notices/n0/publish")
        assertEquals(400, code)
        assertEquals("""{"error":"Add a message or at least one photo first"}""", body)
    }

    @Test fun noticePhotoUploadsAreCheckedLikeSlideshowPhotos() {
        call("POST", "/api/notices", """{"text":"x","untilMs":5000}""")
        assertEquals(400, multipart("x.exe", jpegMagic, "/api/notices/n0/photos").first)
        assertEquals(400, multipart("x.png", jpegMagic, "/api/notices/n0/photos").first)
        assertEquals(200, multipart("../../evil.jpg", jpegMagic, "/api/notices/n0/photos").first)
        assertEquals(listOf("evil.jpg"), backend.noticePhotos["n0"])
        backend.storageFull = true
        assertEquals(507, multipart("p.jpg", jpegMagic, "/api/notices/n0/photos").first)
    }

    @Test fun oldMessageEndpointIsGone() {
        assertEquals(404, call("POST", "/api/message", """{"text":"Hi"}""").first)
    }

    // --- Phones ---

    private val bodyErrorNumber = """{"error":"A number must be 1 to 20 characters: digits, spaces and + / - ( )"}"""

    @Test fun statusListsPhones() {
        val body = call("GET", "/api/status").second
        assertTrue(body, body.contains("\"phones\":[{\"name\":\"Policija\",\"number\":\"192\"},"))
    }

    @Test fun replacesPhonesTrimmedAndInOrder() {
        val (code, body) = call("POST", "/api/phones",
            """{"contacts":[{"name":" Lift Ђ ","number":"021 123 456"},{"name":"Upravnik","number":"+381 (64) 123/4567"}]}""",
            charset = Charsets.UTF_8)
        assertEquals(body, 200, code)
        assertEquals(listOf(PhoneContact("Lift Ђ", "021 123 456"), PhoneContact("Upravnik", "+381 (64) 123/4567")), backend.phones)
        assertTrue(call("GET", "/api/status").second.contains("\"phones\":[{\"name\":\"Lift"))
    }

    @Test fun acceptsEightAndRefusesNine() {
        val eight = (1..8).joinToString(",") { """{"name":"N$it","number":"06$it"}""" }
        assertEquals(200, call("POST", "/api/phones", """{"contacts":[$eight]}""").first)
        assertEquals(8, backend.phones.size)
        val (code, body) = call("POST", "/api/phones", """{"contacts":[$eight,{"name":"N9","number":"069"}]}""")
        assertEquals(400, code)
        assertEquals("""{"error":"At most 8 contacts"}""", body)
        assertEquals(8, backend.phones.size)
    }

    @Test fun refusesBadNames() {
        for (name in listOf("", "   ", "a".repeat(31), "Up\\nravnik")) {
            val (code, body) = call("POST", "/api/phones", """{"contacts":[{"name":"$name","number":"064"}]}""")
            assertEquals(name, 400, code)
            assertEquals("""{"error":"Each contact needs a name of 1 to 30 characters"}""", body)
        }
        assertEquals(PhoneContacts.DEFAULTS, backend.phones)
    }

    @Test fun refusesBadNumberCharacters() {
        for (number in listOf("064x123", "064;rm -rf", "<b>064</b>", "", "1".repeat(21))) {
            val (code, body) = call("POST", "/api/phones", """{"contacts":[{"name":"A","number":"$number"}]}""")
            assertEquals(number, 400, code)
            assertEquals(bodyErrorNumber, body)
        }
        assertEquals(PhoneContacts.DEFAULTS, backend.phones)
    }

    @Test fun emptyListIsSaved() {
        assertEquals(200, call("POST", "/api/phones", """{"contacts":[]}""").first)
        assertEquals(emptyList<PhoneContact>(), backend.phones)
        assertTrue(call("GET", "/api/status").second.contains("\"phones\":[]"))
    }

    @Test fun refusesMalformedPhoneBodies() {
        for (bad in listOf("garbage", "{}", """{"contacts":"x"}""", """{"contacts":[null]}""", """{"contacts":[{"name":"A"}]}""")) {
            assertEquals(bad, 400, call("POST", "/api/phones", bad).first)
        }
        assertEquals(PhoneContacts.DEFAULTS, backend.phones)
    }

    @Test fun phonesNeedThePinAndASmallBody() {
        assertEquals(401, call("POST", "/api/phones", """{"contacts":[]}""", pin = null).first)
        assertEquals(401, call("POST", "/api/phones", """{"contacts":[]}""", pin = "0000").first)
        val big = "a".repeat(70 * 1024)
        assertEquals(413, call("POST", "/api/phones", """{"contacts":[{"name":"$big","number":"1"}]}""").first)
        assertEquals(PhoneContacts.DEFAULTS, backend.phones)
    }

    @Test fun phonesRespectTheLockout() {
        repeat(5) { call("GET", "/api/status", pin = "0000") }
        assertEquals(429, call("POST", "/api/phones", """{"contacts":[]}""").first)
        assertEquals(PhoneContacts.DEFAULTS, backend.phones)
    }
}
