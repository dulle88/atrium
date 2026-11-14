package io.github.dulle88.atrium.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey
    val id: String,
    val filename: String,
    val type: String, // "image", "pdf", or "video"
    val sha256: String?,
    val durationSec: Int,
    val page: Int?,
    val localPath: String,
    val modifiedUtc: String?
)
