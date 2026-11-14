package io.github.dulle88.atrium.ui.components

import io.github.dulle88.atrium.portal.Notice

/** One step of the notice player: [photo] is a file name under `filesDir/notices/`, or null for a text-only slide. */
data class NoticeSlide(val key: String, val text: String?, val photo: String?, val durationMs: Long)

object NoticeSlides {
    const val TEXT_ONLY_MS = 15_000L

    /** Notices one after another: each photo for [photoSeconds] with the notice text over it, a text-only notice for 15 s. */
    fun build(notices: List<Notice>, photoSeconds: Int): List<NoticeSlide> = notices.flatMap { n ->
        val text = n.text?.takeIf { it.isNotBlank() }
        if (n.photos.isEmpty()) {
            listOfNotNull(text?.let { NoticeSlide("${n.id}#text", it, null, TEXT_ONLY_MS) })
        } else {
            n.photos.mapIndexed { i, photo -> NoticeSlide("${n.id}#$i", text, photo, photoSeconds * 1000L) }
        }
    }

    /** Font scale by text length: full size for short text, about 55 % of it beyond 120 characters. */
    fun textScale(length: Int): Float = when {
        length <= 40 -> 1f
        length <= 80 -> 0.82f
        length <= 120 -> 0.68f
        else -> 0.55f
    }
}
