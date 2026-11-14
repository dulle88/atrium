package io.github.dulle88.atrium.util

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import io.github.dulle88.atrium.data.db.MediaItem
import java.io.File

object UsbMediaScanner {

    private const val TAG = "UsbMediaScanner"

    private val SUPPORTED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", // images
        "mp4",                        // video
        "pdf"                         // pdf
    )

    fun scanForMedia(context: Context): List<MediaItem> {
        val usbRoots = getUsbMountPaths(context)
        if (usbRoots.isEmpty()) {
            Log.d(TAG, "No USB volumes found")
            return emptyList()
        }

        val items = mutableListOf<MediaItem>()
        for (root in usbRoots) {
            Log.d(TAG, "Scanning USB root: ${root.absolutePath}")
            walkDirectory(root, items)
        }
        Log.d(TAG, "Found ${items.size} media files on USB")
        return items
    }

    fun isUsbMounted(context: Context): Boolean {
        return getUsbMountPaths(context).isNotEmpty()
    }

    private fun getUsbMountPaths(context: Context): List<File> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val paths = mutableListOf<File>()

        try {
            // Use reflection to access getVolumeList on older APIs
            val getVolumeList = StorageManager::class.java.getMethod("getVolumeList")
            val volumes = getVolumeList.invoke(storageManager) as Array<*>

            for (volume in volumes) {
                val volumeClass = volume!!.javaClass
                val isRemovable = volumeClass.getMethod("isRemovable").invoke(volume) as Boolean
                val getState = volumeClass.getMethod("getState").invoke(volume) as String
                val getPath = volumeClass.getMethod("getPath").invoke(volume) as String

                if (isRemovable && getState == "mounted") {
                    val dir = File(getPath)
                    if (dir.exists() && dir.canRead()) {
                        paths.add(dir)
                        Log.d(TAG, "Found mounted USB volume: $getPath")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating storage volumes via reflection", e)
        }

        // Fallback: check common USB mount points
        if (paths.isEmpty()) {
            val fallbackPaths = listOf(
                "/mnt/media_rw",
                "/storage",
                "/mnt/usb"
            )
            for (base in fallbackPaths) {
                val dir = File(base)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { child ->
                        if (child.isDirectory && child.canRead() && child.name != "emulated" && child.name != "self") {
                            // Check it actually has files (i.e. is mounted)
                            val contents = child.listFiles()
                            if (contents != null && contents.isNotEmpty()) {
                                paths.add(child)
                                Log.d(TAG, "Found USB via fallback: ${child.absolutePath}")
                            }
                        }
                    }
                }
            }
        }

        return paths
    }

    private fun walkDirectory(dir: File, items: MutableList<MediaItem>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                walkDirectory(file, items)
            } else {
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val type = when (ext) {
                        "jpg", "jpeg", "png", "gif" -> "image"
                        "mp4" -> "video"
                        "pdf" -> "pdf"
                        else -> continue
                    }
                    val item = MediaItem(
                        id = "usb-${file.absolutePath.hashCode().toString().replace("-", "n")}",
                        filename = file.name,
                        type = type,
                        sha256 = null,
                        durationSec = 30,
                        page = if (type == "pdf") 0 else null,
                        localPath = file.absolutePath,
                        modifiedUtc = null
                    )
                    items.add(item)
                }
            }
        }
    }
}
