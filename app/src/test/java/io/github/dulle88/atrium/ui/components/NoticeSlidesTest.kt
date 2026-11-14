package io.github.dulle88.atrium.ui.components

import io.github.dulle88.atrium.portal.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeSlidesTest {
    private fun notice(id: String, text: String?, vararg photos: String) =
        Notice(id, text, photos.toList(), 0, 10, true, 0)

    @Test fun textOnlyNoticeIsOneFifteenSecondSlide() {
        assertEquals(
            listOf(NoticeSlide("a#text", "Hi", null, 15_000)),
            NoticeSlides.build(listOf(notice("a", "Hi")), photoSeconds = 10)
        )
    }

    @Test fun photosRotateAtTheSlideshowPaceWithTheTextOverEach() {
        val slides = NoticeSlides.build(
            listOf(notice("a", "Hi", "a-0.jpg", "a-1.png"), notice("b", null, "b-0.jpg"), notice("c", "Bye")),
            photoSeconds = 30
        )
        assertEquals(
            listOf(
                NoticeSlide("a#0", "Hi", "a-0.jpg", 30_000),
                NoticeSlide("a#1", "Hi", "a-1.png", 30_000),
                NoticeSlide("b#0", null, "b-0.jpg", 30_000),
                NoticeSlide("c#text", "Bye", null, 15_000)
            ),
            slides
        )
    }

    @Test fun blankTextCountsAsNoText() {
        assertEquals(listOf(NoticeSlide("a#0", null, "a-0.jpg", 5_000)),
            NoticeSlides.build(listOf(notice("a", "  ", "a-0.jpg")), photoSeconds = 5))
        assertEquals(emptyList<NoticeSlide>(), NoticeSlides.build(listOf(notice("a", " ")), photoSeconds = 5))
    }

    @Test fun longTextShrinksDownToAboutFiftyFivePercent() {
        assertEquals(1f, NoticeSlides.textScale(40), 0f)
        assertEquals(0.55f, NoticeSlides.textScale(121), 0f)
        assertEquals(0.55f, NoticeSlides.textScale(2000), 0f)
        var previous = 1f
        for (len in 0..300) {
            val s = NoticeSlides.textScale(len)
            assertTrue("scale grew at $len", s <= previous)
            previous = s
        }
    }
}
