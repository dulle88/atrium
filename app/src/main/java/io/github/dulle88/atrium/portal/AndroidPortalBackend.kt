package io.github.dulle88.atrium.portal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import io.github.dulle88.atrium.data.db.AppDatabase
import io.github.dulle88.atrium.data.db.MediaItem
import io.github.dulle88.atrium.data.repository.MediaRepository
import io.github.dulle88.atrium.sky.SkyClock
import io.github.dulle88.atrium.ui.components.SlideshowPlan
import io.github.dulle88.atrium.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AndroidPortalBackend(
    context: Context,
    private val media: MediaRepository,
    private val store: PortalStore,
    private val clock: AppClock
) : PortalBackend {
    private val photoDao = AppDatabase.getInstance(context).mediaItemDao()
    private val filesDir = context.applicationContext.filesDir
    private val noticesDir = File(filesDir, "notices").apply { mkdirs() }
    private val thumbsDir = File(filesDir, "thumbs").apply { mkdirs() }
    private val thumbLock = Any()

    override suspend fun pin() = store.pin()

    override suspend fun setPin(newPin: String) = store.setPin(newPin)

    override suspend fun setTime(epochMs: Long) = clock.setTime(epochMs)

    override suspend fun status(): PortalStatus {
        val now = clock.now()
        val today = clock.nowZoned().toLocalDate()
        val times = SkyClock.sunTimes(today, AppClock.ZONE)
        fun hhmm(m: Int?) = m?.let { "%02d:%02d".format(it / 60, it % 60) }
        val notices = store.notices(now).filter { it.published }.sortedBy { it.fromMs }
        return PortalStatus(
            nowMs = now,
            sunrise = hhmm(times.sunriseMinute),
            sunset = hhmm(times.sunsetMinute),
            slideshow = SlideshowInfo(store.slideshowSeconds(), slideshowPhotos().map { PhotoInfo(it.id, it.filename) }),
            notices = notices.map { n ->
                NoticeInfo(
                    n.id, n.text, n.photoIds().zip(n.photos).map { (id, name) -> PhotoInfo(id, name) },
                    n.fromMs, n.untilMs, n.stateAt(now).json
                )
            },
            tv = TvSchedule.of(notices, now),
            phones = store.phones()
        )
    }

    // --- Photos ---

    override suspend fun photo(id: String, thumb: Boolean): PhotoFile? {
        val source = photoSource(id)?.takeIf { it.isFile } ?: return null
        if (thumb) {
            val small = withContext(Dispatchers.IO) { thumbFor(id, source) }
            if (small != null) return PhotoFile(small, "image/jpeg")
        }
        val mime = if (source.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
        return PhotoFile(source, mime)
    }

    private suspend fun photoSource(id: String): File? {
        val noticePhoto = Notice.parsePhotoId(id)
        if (noticePhoto != null) {
            val (noticeId, index) = noticePhoto
            val notice = store.notices(clock.now()).find { it.id == noticeId }
            notice?.photos?.getOrNull(index)?.let { return File(noticesDir, it) }
        }
        val item = media.getById(id)?.takeIf { it.type == "image" } ?: return null
        return fileOf(item)
    }

    override suspend fun addPhoto(filename: String, source: File): String {
        checkQuota()
        val ext = extensionOf(filename)
        val id = "$PHOTO_PREFIX${UUID.randomUUID()}"
        val dest = File(media.mediaDir, "$id.$ext")
        val shown = slideshowPhotos().map { it.id }
        withContext(Dispatchers.IO) { source.copyTo(dest, overwrite = true) }
        media.insert(
            MediaItem(
                id = id, filename = filename, type = "image", sha256 = null,
                durationSec = store.slideshowSeconds(), page = null, localPath = dest.absolutePath, modifiedUtc = null
            )
        )
        store.appendToSlideshow(id, shown)
        return id
    }

    /** Deletes any slideshow image, legacy synced items included. Only files inside app storage are removed. */
    override suspend fun deletePhoto(id: String): Boolean {
        val item = media.getById(id)?.takeIf { it.type == "image" } ?: return false
        withContext(Dispatchers.IO) {
            val file = fileOf(item)
            if (isInsideAppStorage(file)) file.delete() else Log.w(TAG, "not deleting file outside app storage")
            File(thumbsDir, "$id$THUMB_SUFFIX").delete()
        }
        media.delete(id)
        store.removeFromSlideshow(id)
        return true
    }

    override suspend fun setSlideshowOrder(ids: List<String>) {
        val known = slideshowItems().mapTo(HashSet()) { it.id }
        store.saveSlideshowOrder(ids.filter { it in known })
    }

    override suspend fun setSlideshowSeconds(seconds: Int) = store.setSlideshowSeconds(seconds)

    // --- Notices ---

    override suspend fun createNotice(text: String?, fromMs: Long?, untilMs: Long): String =
        store.createDraft(text, fromMs, untilMs, clock.now()).id

    override suspend fun addNoticePhoto(noticeId: String, filename: String, source: File): String {
        checkQuota()
        val updated = store.addNoticePhoto(noticeId, extensionOf(filename), clock.now()) { name ->
            withContext(Dispatchers.IO) { source.copyTo(File(noticesDir, name), overwrite = true) }
        }
        return updated.photoIds().last()
    }

    override suspend fun publishNotice(noticeId: String) {
        store.publish(noticeId, clock.now())
    }

    override suspend fun deleteNotice(noticeId: String): Boolean {
        val removed = store.deleteNotice(noticeId, clock.now()) ?: return false
        deleteNoticeFiles(removed)
        return true
    }

    /** Removes expired notices and stale drafts with their files. Returns the published notices that remain. */
    suspend fun purgeNotices(): List<Notice> {
        val result = store.purge(clock.now())
        result.removed.forEach { deleteNoticeFiles(it) }
        return result.kept.filter { it.published }
    }

    private suspend fun deleteNoticeFiles(notice: Notice) = withContext(Dispatchers.IO) {
        notice.photos.forEach { File(noticesDir, it).delete() }
        notice.photoIds().forEach { File(thumbsDir, "$it$THUMB_SUFFIX").delete() }
    }

    override suspend fun setPhones(contacts: List<PhoneContact>) = store.setPhones(contacts)

    // --- Helpers ---

    private suspend fun slideshowItems() = photoDao.getAll().filter { it.type == "image" }

    private suspend fun slideshowPhotos() =
        SlideshowPlan.arrange(slideshowItems(), store.slideshowOrder(), store.slideshowSeconds())

    private suspend fun checkQuota() {
        val noticePhotos = store.notices(clock.now()).sumOf { it.photos.size }
        PortalRules.checkPhotoQuota(media.mediaDir.usableSpace, slideshowItems().size + noticePhotos)
    }

    private fun extensionOf(filename: String) =
        filename.substringAfterLast('.', "jpg").lowercase().let { if (it == "jpeg") "jpg" else it }

    private fun fileOf(item: MediaItem) =
        if (item.localPath.startsWith("/")) File(item.localPath) else media.getMediaFile(item.localPath)

    private fun isInsideAppStorage(file: File): Boolean =
        runCatching { file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator) }.getOrDefault(false)

    /** A JPEG about [THUMB_PX] on the long side, cached under `filesDir/thumbs`. One at a time, to spare memory. */
    private fun thumbFor(id: String, source: File): File? = synchronized(thumbLock) {
        val out = File(thumbsDir, "$id$THUMB_SUFFIX")
        if (out.isFile && out.lastModified() >= source.lastModified()) return out
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (longSide <= 0) return null
            var sample = 1
            while (longSide / (sample * 2) >= THUMB_PX) sample *= 2
            val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val matrix = Matrix()
            val scale = THUMB_PX / maxOf(decoded.width, decoded.height).toFloat()
            if (scale < 1f) matrix.postScale(scale, scale)
            matrix.postRotate(exifRotation(source).toFloat())
            val thumb = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val tmp = File(thumbsDir, "$id.tmp")
            FileOutputStream(tmp).use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (thumb !== decoded) thumb.recycle()
            decoded.recycle()
            if (!tmp.renameTo(out)) return null
            out
        } catch (e: Exception) {
            Log.w(TAG, "thumbnail failed", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "thumbnail out of memory")
            null
        }
    }

    private fun exifRotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private companion object {
        const val TAG = "PortalBackend"
        const val PHOTO_PREFIX = "web-"
        const val THUMB_SUFFIX = ".thumb.jpg"
        const val THUMB_PX = 320
    }
}
