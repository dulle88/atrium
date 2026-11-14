package io.github.dulle88.atrium.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeTest {
    private fun notice(id: String, from: Long, until: Long, published: Boolean = true, text: String? = "t",
                       photos: List<String> = emptyList()) = Notice(id, text, photos, from, until, published, 0)

    @Test fun stateFollowsTheWindow() {
        val n = notice("a", 100, 200)
        assertEquals(NoticeState.SCHEDULED, n.stateAt(99))
        assertEquals(NoticeState.ACTIVE, n.stateAt(100))
        assertEquals(NoticeState.ACTIVE, n.stateAt(199))
        assertEquals(NoticeState.EXPIRED, n.stateAt(200))
    }

    @Test fun onlyPublishedNoticesWithContentAreShown() {
        assertTrue(notice("a", 0, 10).isShownAt(5))
        assertFalse(notice("a", 0, 10, published = false).isShownAt(5))
        assertFalse(notice("a", 0, 10, text = null).isShownAt(5))
        assertFalse(notice("a", 0, 10, text = "  ").isShownAt(5))
        assertTrue(notice("a", 0, 10, text = null, photos = listOf("a-0.jpg")).isShownAt(5))
        assertFalse(notice("a", 0, 10).isShownAt(10))
    }

    @Test fun photoIdsUseNoticeIdAndIndex() {
        assertEquals(listOf("n-abc-0", "n-abc-1"), notice("abc", 0, 1, photos = listOf("abc-0.jpg", "abc-1.png")).photoIds())
        assertEquals("abc" to 1, Notice.parsePhotoId("n-abc-1"))
        assertEquals("a-b" to 12, Notice.parsePhotoId("n-a-b-12"))
        assertEquals(null, Notice.parsePhotoId("web-abc-1"))
        assertEquals(null, Notice.parsePhotoId("n-abc-x"))
        assertEquals(null, Notice.parsePhotoId("n--1"))
    }

    @Test fun tvShowsTheSlideshowWithTheNextStart() {
        val list = listOf(
            notice("s1", 500, 900), notice("s2", 300, 900), notice("d", 200, 900, published = false)
        )
        assertEquals(TvInfo("slideshow", null, 300), TvSchedule.of(list, 100))
        assertEquals(TvInfo("slideshow", null, null), TvSchedule.of(emptyList(), 100))
    }

    @Test fun tvShowsNoticesUntilTheEarliestActiveEnd() {
        val list = listOf(notice("a", 0, 900), notice("b", 50, 400), notice("later", 600, 700))
        assertEquals(TvInfo("notice", 400, null), TvSchedule.of(list, 100))
    }
}
