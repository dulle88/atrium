package io.github.dulle88.atrium.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kv")
data class Kv(
    @PrimaryKey
    val k: String,
    val v: String
)
