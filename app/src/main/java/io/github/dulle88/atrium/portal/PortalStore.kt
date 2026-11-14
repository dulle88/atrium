package io.github.dulle88.atrium.portal

import com.google.gson.Gson
import io.github.dulle88.atrium.data.db.KvStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/**
 * Portal state kept in Kv: the PIN, slideshow order and pace, notices and the phone contacts.
 *
 * Every notice change is a read-modify-write under one [Mutex], so the expiry purge (a compare and
 * delete) never wipes a notice saved meanwhile. Times always come from the caller (AppClock).
 */
class PortalStore(
    private val kv: KvStore,
    private val newId: () -> String = ::randomId
) {
    private val gson = Gson()
    private val pinLock = Mutex()
    private val noticeLock = Mutex()
    private val slideshowLock = Mutex()

    class Purge(val kept: List<Notice>, val removed: List<Notice>)

    /** The pre-notice single message, read once for migration. */
    private class LegacyMessage(val text: String?, val expiresAtMs: Long?)

    // --- PIN ---

    suspend fun pin(): String = pinLock.withLock {
        kv.get(KEY_PIN)?.let { return@withLock it }
        val created = SecureRandom().nextInt(10_000).toString().padStart(4, '0')
        kv.put(KEY_PIN, created)
        created
    }

    /** Replaces the PIN. Exactly four ASCII digits. */
    suspend fun setPin(newPin: String) = pinLock.withLock {
        require(newPin.length == 4 && newPin.all { it in '0'..'9' }) { "PIN must be 4 digits" }
        kv.put(KEY_PIN, newPin)
    }

    // --- Slideshow ---

    suspend fun slideshowSeconds(): Int =
        kv.get(KEY_SECONDS)?.toIntOrNull()?.takeIf { it in SLIDESHOW_SECONDS } ?: DEFAULT_SECONDS

    suspend fun setSlideshowSeconds(seconds: Int) {
        if (seconds !in SLIDESHOW_SECONDS) throw InvalidRequestException("Invalid duration")
        kv.put(KEY_SECONDS, seconds.toString())
    }

    suspend fun slideshowOrder(): List<String> =
        kv.get(KEY_ORDER)?.let { raw ->
            runCatching { gson.fromJson(raw, Array<String>::class.java) }.getOrNull()
        }?.filterNotNull()?.distinct() ?: emptyList()

    suspend fun saveSlideshowOrder(ids: List<String>) = slideshowLock.withLock { writeOrder(ids) }

    /**
     * Puts [id] last. [shownIds] is the slideshow as it plays now; ids it has that the stored order
     * lacks are written first, so a new photo never jumps ahead of photos that were never reordered.
     */
    suspend fun appendToSlideshow(id: String, shownIds: List<String>) = slideshowLock.withLock {
        val order = slideshowOrder()
        if (id !in order) writeOrder(order + shownIds.filter { it !in order && it != id } + id)
    }

    suspend fun removeFromSlideshow(id: String) = slideshowLock.withLock { writeOrder(slideshowOrder() - id) }

    private suspend fun writeOrder(ids: List<String>) = kv.put(KEY_ORDER, gson.toJson(ids.distinct()))

    // --- Phones ---

    /** The contacts shown on the TV: [PhoneContacts.DEFAULTS] until a list is saved, also if the stored one is unreadable. */
    suspend fun phones(): List<PhoneContact> {
        val raw = kv.get(KEY_PHONES) ?: return PhoneContacts.DEFAULTS
        return runCatching {
            PhoneContacts.validate(gson.fromJson(raw, Array<PhoneContact?>::class.java).toList())
        }.getOrDefault(PhoneContacts.DEFAULTS)
    }

    /** Replaces the whole list (an empty one is allowed). Throws [InvalidRequestException] if a contact is invalid. */
    suspend fun setPhones(contacts: List<PhoneContact>) {
        kv.put(KEY_PHONES, gson.toJson(PhoneContacts.validate(contacts)))
    }

    // --- Notices ---

    /** All notices, drafts included. Migrates a legacy single message on first read. */
    suspend fun notices(nowMs: Long): List<Notice> = noticeLock.withLock { load(nowMs) }

    suspend fun createDraft(text: String?, fromMs: Long?, untilMs: Long, nowMs: Long): Notice = noticeLock.withLock {
        val clean = text?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null && clean.length > MAX_TEXT) {
            throw InvalidRequestException("Message is longer than $MAX_TEXT characters")
        }
        val from = fromMs ?: nowMs
        checkWindow(from, untilMs, nowMs)
        val list = load(nowMs)
        if (list.size >= MAX_NOTICES) throw InvalidRequestException("Too many notices. Remove one first.")
        val notice = Notice(newId(), clean, emptyList(), from, untilMs, published = false, createdMs = nowMs)
        save(list + notice)
        notice
    }

    /**
     * Adds a photo to a draft. [write] receives the new file name and must store the file; the notice
     * only records it if [write] returns normally.
     */
    suspend fun addNoticePhoto(id: String, ext: String, nowMs: Long, write: suspend (String) -> Unit): Notice =
        noticeLock.withLock {
            val list = load(nowMs)
            val notice = list.find { it.id == id } ?: throw NoticeNotFoundException()
            if (notice.published) throw InvalidRequestException("Notice is already on the TV")
            if (notice.photos.size >= MAX_PHOTOS) throw InvalidRequestException("A notice can have at most $MAX_PHOTOS photos")
            val name = "$id-${notice.photos.size}.$ext"
            write(name)
            val updated = notice.copy(photos = notice.photos + name)
            save(list.map { if (it.id == id) updated else it })
            updated
        }

    suspend fun publish(id: String, nowMs: Long): Notice = noticeLock.withLock {
        val list = load(nowMs)
        val notice = list.find { it.id == id } ?: throw NoticeNotFoundException()
        if (!notice.hasContent()) throw InvalidRequestException("Add a message or at least one photo first")
        checkWindow(notice.fromMs, notice.untilMs, nowMs)
        val updated = notice.copy(published = true)
        save(list.map { if (it.id == id) updated else it })
        updated
    }

    /** Removes the notice and returns it so the caller can delete its files, or null if it did not exist. */
    suspend fun deleteNotice(id: String, nowMs: Long): Notice? = noticeLock.withLock {
        val list = load(nowMs)
        val notice = list.find { it.id == id } ?: return@withLock null
        save(list - notice)
        notice
    }

    /** Drops expired published notices and drafts older than an hour, in one locked step. */
    suspend fun purge(nowMs: Long): Purge = noticeLock.withLock {
        val list = load(nowMs)
        val (removed, kept) = list.partition {
            (it.published && it.untilMs <= nowMs) || (!it.published && nowMs - it.createdMs > DRAFT_TTL_MS)
        }
        if (removed.isNotEmpty()) save(kept)
        Purge(kept, removed)
    }

    private fun checkWindow(fromMs: Long, untilMs: Long, nowMs: Long) {
        if (untilMs <= nowMs) throw InvalidRequestException("The end time has already passed")
        if (untilMs >= nowMs + YEAR_MS) throw InvalidRequestException("The end time is more than a year away")
        if (fromMs >= nowMs + YEAR_MS) throw InvalidRequestException("The start is more than a year away")
        if (fromMs >= untilMs) throw InvalidRequestException("The start must be before the end")
    }

    /** Call with [noticeLock] held. */
    private suspend fun load(nowMs: Long): List<Notice> {
        val stored = kv.get(KEY_NOTICES)?.let(::parseNotices) ?: emptyList()
        val legacyRaw = kv.get(KEY_LEGACY_MESSAGE) ?: return stored
        val legacy = runCatching { gson.fromJson(legacyRaw, LegacyMessage::class.java) }.getOrNull()
        val text = legacy?.text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
        val expires = legacy?.expiresAtMs
        val migrated = if (text != null && (expires == null || expires > nowMs)) {
            val until = minOf(expires ?: Long.MAX_VALUE, nowMs + YEAR_MS - MINUTE_MS)
            stored + Notice(newId(), text, emptyList(), nowMs, until, published = true, createdMs = nowMs)
        } else {
            stored
        }
        save(migrated)
        kv.delete(KEY_LEGACY_MESSAGE)
        return migrated
    }

    @Suppress("SENSELESS_COMPARISON") // Gson can leave non-null Kotlin fields null
    private fun parseNotices(raw: String): List<Notice> {
        val parsed = runCatching { gson.fromJson(raw, Array<Notice>::class.java) }.getOrNull() ?: return emptyList()
        return parsed.filterNotNull().filter { it.id != null }.map { if (it.photos == null) it.copy(photos = emptyList()) else it }
    }

    private suspend fun save(list: List<Notice>) = kv.put(KEY_NOTICES, gson.toJson(list))

    companion object {
        val SLIDESHOW_SECONDS = setOf(5, 10, 15, 30, 60)
        const val DEFAULT_SECONDS = 10
        const val MAX_NOTICES = 20
        const val MAX_PHOTOS = 10
        const val MAX_TEXT = 2000
        private const val MINUTE_MS = 60_000L
        private const val DRAFT_TTL_MS = 60 * MINUTE_MS
        private const val YEAR_MS = 365L * 86_400_000L
        private const val KEY_PIN = "portal_pin"
        private const val KEY_ORDER = "slideshow_order"
        private const val KEY_SECONDS = "slideshow_seconds"
        private const val KEY_NOTICES = "notices"
        private const val KEY_LEGACY_MESSAGE = "portal_message"
        private const val KEY_PHONES = "phone_contacts"

        private fun randomId(): String {
            val bytes = ByteArray(6)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
