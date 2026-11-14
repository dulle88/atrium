package io.github.dulle88.atrium.ui.screens

import io.github.dulle88.atrium.portal.Notice
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryResolverTest {
    private fun notice(id: String, from: Long, until: Long, published: Boolean = true, text: String? = "t") =
        Notice(id, text, emptyList(), from, until, published, 0)

    @Test fun activeNoticesBeatTheSlideshowSortedByStart() {
        val a = notice("a", 500, 2_000)
        val b = notice("b", 100, 2_000)
        assertEquals(GalleryContent.Notices(listOf(b, a)), GalleryResolver.resolve(listOf(a, b), nowMs = 1_000))
    }

    @Test fun expiredScheduledDraftAndEmptyNoticesAreIgnored() {
        val list = listOf(
            notice("expired", 0, 1_000), notice("scheduled", 1_001, 5_000),
            notice("draft", 0, 5_000, published = false), notice("empty", 0, 5_000, text = " ")
        )
        assertEquals(GalleryContent.Slideshow, GalleryResolver.resolve(list, nowMs = 1_000))
    }

    @Test fun noNoticesShowsTheSlideshow() {
        assertEquals(GalleryContent.Slideshow, GalleryResolver.resolve(emptyList(), 1_000))
    }

    @Test fun scheduledNoticeBecomesActiveWhenItsStartPasses() {
        val n = notice("s", 1_000, 2_000)
        assertEquals(GalleryContent.Slideshow, GalleryResolver.resolve(listOf(n), 999))
        assertEquals(GalleryContent.Notices(listOf(n)), GalleryResolver.resolve(listOf(n), 1_000))
        assertEquals(GalleryContent.Slideshow, GalleryResolver.resolve(listOf(n), 2_000))
    }
}
