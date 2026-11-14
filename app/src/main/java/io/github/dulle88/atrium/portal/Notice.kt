package io.github.dulle88.atrium.portal

/**
 * A temporary takeover of the gallery area: a message, photos, or both, between [fromMs] and [untilMs].
 * Stored as JSON in Kv (Gson field names are the storage format). [photos] are file names under
 * `filesDir/notices/`. A notice is a draft until [published]; drafts are purged after an hour.
 */
data class Notice(
    val id: String,
    val text: String?,
    val photos: List<String>,
    val fromMs: Long,
    val untilMs: Long,
    val published: Boolean,
    val createdMs: Long
) {
    fun stateAt(nowMs: Long): NoticeState = when {
        untilMs <= nowMs -> NoticeState.EXPIRED
        fromMs > nowMs -> NoticeState.SCHEDULED
        else -> NoticeState.ACTIVE
    }

    fun hasContent(): Boolean = !text.isNullOrBlank() || photos.isNotEmpty()

    /** True when the TV should show this notice now. */
    fun isShownAt(nowMs: Long): Boolean = published && hasContent() && stateAt(nowMs) == NoticeState.ACTIVE

    fun photoIds(): List<String> = photos.indices.map { "$PHOTO_ID_PREFIX$id-$it" }

    companion object {
        const val PHOTO_ID_PREFIX = "n-"

        /** Splits `n-<noticeId>-<index>` into the notice id and the photo index, or null. */
        fun parsePhotoId(photoId: String): Pair<String, Int>? {
            if (!photoId.startsWith(PHOTO_ID_PREFIX)) return null
            val rest = photoId.removePrefix(PHOTO_ID_PREFIX)
            val dash = rest.lastIndexOf('-')
            if (dash <= 0) return null
            val index = rest.substring(dash + 1).toIntOrNull() ?: return null
            return rest.substring(0, dash) to index
        }
    }
}

enum class NoticeState(val json: String) { SCHEDULED("scheduled"), ACTIVE("active"), EXPIRED("expired") }

/** What the TV gallery shows, as the portal reports it. */
object TvSchedule {
    fun of(notices: List<Notice>, nowMs: Long): TvInfo {
        val shown = notices.filter { it.isShownAt(nowMs) }
        if (shown.isNotEmpty()) return TvInfo("notice", shown.minOf { it.untilMs }, null)
        val next = notices.filter { it.published && it.hasContent() && it.stateAt(nowMs) == NoticeState.SCHEDULED }
            .minOfOrNull { it.fromMs }
        return TvInfo("slideshow", null, next)
    }
}
