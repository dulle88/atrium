package io.github.dulle88.atrium.ui.screens

import io.github.dulle88.atrium.portal.Notice

sealed interface GalleryContent {
    data class Notices(val notices: List<Notice>) : GalleryContent
    data object Slideshow : GalleryContent
}

/** What the gallery shows: active published notices (by start time), otherwise the slideshow. Holiday greetings live in the top bar. */
object GalleryResolver {
    fun resolve(notices: List<Notice>, nowMs: Long): GalleryContent {
        val shown = notices.filter { it.isShownAt(nowMs) }.sortedBy { it.fromMs }
        return if (shown.isEmpty()) GalleryContent.Slideshow else GalleryContent.Notices(shown)
    }
}
