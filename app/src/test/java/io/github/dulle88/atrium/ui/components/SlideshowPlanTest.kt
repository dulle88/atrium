package io.github.dulle88.atrium.ui.components

import io.github.dulle88.atrium.data.db.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SlideshowPlanTest {
    private fun item(id: String, type: String = "image", sec: Int = 7) =
        MediaItem(id, "$id.jpg", type, null, sec, null, "/x/$id.jpg", null)

    @Test fun followsTheStoredOrderAppendsMissingAndDropsUnknown() {
        val items = listOf(item("a"), item("b"), item("c"), item("d"))
        val arranged = SlideshowPlan.arrange(items, listOf("c", "gone", "a"), seconds = 10)
        assertEquals(listOf("c", "a", "b", "d"), arranged.map { it.id })
    }

    @Test fun storedSecondsReplaceEachImageDuration() {
        val arranged = SlideshowPlan.arrange(listOf(item("a"), item("v", type = "video", sec = 300)), emptyList(), 15)
        assertEquals(listOf(15, 300), arranged.map { it.durationSec })
    }

    @Test fun duplicateIdsInTheOrderAreIgnored() {
        assertEquals(listOf("b", "a"), SlideshowPlan.arrange(listOf(item("a"), item("b")), listOf("b", "b", "a"), 10).map { it.id })
    }
}
