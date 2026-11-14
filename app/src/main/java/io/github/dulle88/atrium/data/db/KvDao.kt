package io.github.dulle88.atrium.data.db

import androidx.room.*

@Dao
interface KvDao {
    @Query("SELECT v FROM kv WHERE k = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(kv: Kv)

    @Query("DELETE FROM kv WHERE k = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM kv")
    suspend fun deleteAll()
}
