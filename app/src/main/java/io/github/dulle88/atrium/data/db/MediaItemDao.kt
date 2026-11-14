package io.github.dulle88.atrium.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY id")
    fun getAllFlow(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY id")
    suspend fun getAll(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItem>)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Query("DELETE FROM media_items WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)
}
