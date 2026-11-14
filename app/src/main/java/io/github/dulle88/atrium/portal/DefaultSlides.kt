package io.github.dulle88.atrium.portal

import android.content.Context
import android.util.Log
import io.github.dulle88.atrium.data.db.KvStore
import io.github.dulle88.atrium.data.db.MediaItem
import io.github.dulle88.atrium.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bundled photos (assets/defaults) that fill an empty slideshow on first run. Seeded once, so
 * photos the user deletes in the portal stay deleted, and never when the slideshow already has items.
 */
object DefaultSlides {
    private const val TAG = "DefaultSlides"
    private const val KEY_SEEDED = "default_slides_seeded"
    private const val DIR = "defaults"

    suspend fun seedIfFirstRun(context: Context, kv: KvStore, media: MediaRepository, seconds: Int) {
        if (kv.get(KEY_SEEDED) != null) return
        try {
            val names = context.assets.list(DIR)?.sorted().orEmpty()
            if (media.hasAny()) { kv.put(KEY_SEEDED, "skipped"); return }
            for (name in names) {
                val id = "default-${name.substringBeforeLast('.')}"
                val dest = File(media.mediaDir, "$id.jpg")
                withContext(Dispatchers.IO) {
                    context.assets.open("$DIR/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
                }
                media.insert(
                    MediaItem(id = id, filename = name, type = "image", sha256 = null, durationSec = seconds,
                        page = null, localPath = dest.absolutePath, modifiedUtc = null)
                )
            }
            kv.put(KEY_SEEDED, "ok")
        } catch (e: Exception) {
            Log.w(TAG, "seeding default slides failed; will retry next start", e)
        }
    }
}
