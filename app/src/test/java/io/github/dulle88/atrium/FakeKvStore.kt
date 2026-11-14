package io.github.dulle88.atrium

import io.github.dulle88.atrium.data.db.KvStore

class FakeKvStore : KvStore {
    val map = mutableMapOf<String, String>()
    override suspend fun get(key: String) = map[key]
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun delete(key: String) { map.remove(key) }
}
