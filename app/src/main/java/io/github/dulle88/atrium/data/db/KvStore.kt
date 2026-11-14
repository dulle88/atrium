package io.github.dulle88.atrium.data.db

interface KvStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun delete(key: String)
}

class RoomKvStore(private val dao: KvDao) : KvStore {
    override suspend fun get(key: String) = dao.get(key)
    override suspend fun put(key: String, value: String) = dao.put(Kv(key, value))
    override suspend fun delete(key: String) = dao.delete(key)
}
