package io.github.dulle88.atrium.ui.components

import io.github.dulle88.atrium.data.db.MediaItem

object SlideshowPlan {
    /**
     * Puts [items] in the stored [order] (ids missing from it go to the end in their current order,
     * unknown ids are ignored) and gives every image the stored [seconds].
     */
    fun arrange(items: List<MediaItem>, order: List<String>, seconds: Int): List<MediaItem> {
        val byId = items.associateBy { it.id }
        val ordered = order.distinct().mapNotNull { byId[it] }
        val placed = ordered.mapTo(HashSet()) { it.id }
        return (ordered + items.filter { it.id !in placed })
            .map { if (it.type == "image" && it.durationSec != seconds) it.copy(durationSec = seconds) else it }
    }
}
