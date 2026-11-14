package io.github.dulle88.atrium.data.repository

import android.content.Context
import io.github.dulle88.atrium.data.db.AppDatabase
import io.github.dulle88.atrium.data.db.MediaItem
import kotlinx.coroutines.flow.Flow
import java.io.File

class MediaRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).mediaItemDao()
    private val appContext = context.applicationContext

    val mediaDir = File(appContext.filesDir, "media").apply { mkdirs() }
    private val thumbsDir = File(appContext.filesDir, "thumbs").apply { mkdirs() }

    fun mediaItemsFlow(): Flow<List<MediaItem>> = dao.getAllFlow()
    fun getMediaFile(localPath: String): File = File(mediaDir, localPath)
    fun getThumbFile(id: String): File = File(thumbsDir, "$id.png")
    suspend fun hasAny(): Boolean = dao.getAll().isNotEmpty()
    suspend fun insert(item: MediaItem) = dao.insert(item)
    suspend fun getById(id: String): MediaItem? = dao.getById(id)
    suspend fun delete(id: String) = dao.deleteById(id)
}
