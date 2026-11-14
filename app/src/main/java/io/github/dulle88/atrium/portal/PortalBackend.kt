package io.github.dulle88.atrium.portal

import java.io.File

// Status DTOs. Gson serialises their field names, so they are kept by R8 (see proguard-rules.pro).
data class PhotoInfo(val id: String, val filename: String)

data class SlideshowInfo(val seconds: Int, val photos: List<PhotoInfo>)

data class NoticeInfo(
    val id: String,
    val text: String?,
    val photos: List<PhotoInfo>,
    val fromMs: Long,
    val untilMs: Long,
    val state: String
)

/** [mode] is "notice" or "slideshow". */
data class TvInfo(val mode: String, val untilMs: Long?, val nextFromMs: Long?)

data class PortalStatus(
    val nowMs: Long,
    val sunrise: String?,
    val sunset: String?,
    val slideshow: SlideshowInfo,
    val notices: List<NoticeInfo>,
    val tv: TvInfo,
    val phones: List<PhoneContact>
)

/** A photo file to send back, with its content type. */
class PhotoFile(val file: File, val mime: String)

interface PortalBackend {
    suspend fun pin(): String
    suspend fun setPin(newPin: String)
    suspend fun status(): PortalStatus
    suspend fun setTime(epochMs: Long)

    /** The photo (or a ~320 px JPEG thumbnail) for a slideshow or notice photo id, or null. */
    suspend fun photo(id: String, thumb: Boolean): PhotoFile?

    /** Copies [source] into app storage, appends it to the slideshow and returns the new photo id. */
    suspend fun addPhoto(filename: String, source: File): String
    suspend fun deletePhoto(id: String): Boolean
    suspend fun setSlideshowOrder(ids: List<String>)
    suspend fun setSlideshowSeconds(seconds: Int)

    /** Creates an unpublished draft and returns its id. */
    suspend fun createNotice(text: String?, fromMs: Long?, untilMs: Long): String
    /** Attaches a photo to a draft and returns the photo id. */
    suspend fun addNoticePhoto(noticeId: String, filename: String, source: File): String
    suspend fun publishNotice(noticeId: String)
    suspend fun deleteNotice(noticeId: String): Boolean

    /** Replaces the TV's phone contacts. [contacts] are already validated by [PhoneContacts.validate]. */
    suspend fun setPhones(contacts: List<PhoneContact>)
}

/** Thrown by a backend to refuse a request. The server answers with [httpStatus] and the message. */
open class PortalRejection(val httpStatus: Int, message: String) : Exception(message)

class InvalidRequestException(message: String) : PortalRejection(400, message)

class NoticeNotFoundException : PortalRejection(404, "Notice not found")

class StorageFullException : PortalRejection(507, "Not enough storage on the TV. Delete some photos first.")

object PortalRules {
    const val MAX_WEB_PHOTOS = 200
    const val MIN_FREE_BYTES = 256L * 1024 * 1024

    fun checkPhotoQuota(usableBytes: Long, webPhotoCount: Int) {
        if (usableBytes < MIN_FREE_BYTES || webPhotoCount >= MAX_WEB_PHOTOS) throw StorageFullException()
    }
}
