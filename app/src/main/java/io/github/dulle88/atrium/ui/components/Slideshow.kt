package io.github.dulle88.atrium.ui.components

import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import io.github.dulle88.atrium.data.db.MediaItem
import io.github.dulle88.atrium.data.repository.MediaRepository
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun Slideshow(
    mediaItems: List<MediaItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }

    var currentIndex by remember { mutableStateOf(0) }

    // Reset index if it's out of bounds (e.g., when media list shrinks)
    LaunchedEffect(mediaItems.size) {
        if (mediaItems.isNotEmpty() && currentIndex >= mediaItems.size) {
            currentIndex = 0
        }
    }

    // Auto-advance slideshow
    LaunchedEffect(mediaItems, currentIndex) {
        if (mediaItems.isEmpty()) return@LaunchedEffect

        val currentItem = mediaItems.getOrNull(currentIndex) ?: return@LaunchedEffect
        val durationMillis = if (currentItem.type == "video") {
            // Videos play for 5 minutes (300 seconds)
            300 * 1000L
        } else {
            // Images and PDFs use their configured duration
            (currentItem.durationSec * 1000).toLong()
        }

        delay(durationMillis)
        currentIndex = (currentIndex + 1) % mediaItems.size
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp)) // Rounded corners matching Figma design
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (mediaItems.isEmpty()) {
            Text(
                text = "No media to display",
                color = Color.White
            )
        } else {
            val currentItem = mediaItems.getOrNull(currentIndex)
            
            if (currentItem != null) {
                Crossfade(
                    targetState = currentItem.id,
                    animationSpec = tween(durationMillis = 1000),
                    label = "slideshow"
                ) { itemId ->
                    val item = mediaItems.find { it.id == itemId } ?: return@Crossfade

                    when (item.type) {
                        "image" -> {
                            val file = if (item.localPath.startsWith("/")) File(item.localPath)
                                       else repository.getMediaFile(item.localPath)
                            DisplayImage(file)
                        }
                        "pdf" -> {
                            DisplayPdf(repository.getThumbFile(item.id))
                        }
                        "video" -> {
                            val file = if (item.localPath.startsWith("/")) File(item.localPath)
                                       else repository.getMediaFile(item.localPath)
                            DisplayVideo(file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayImage(file: File) {
    AsyncImage(
        model = file,
        contentDescription = "Slideshow image",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun DisplayPdf(thumbFile: File) {
    if (thumbFile.exists()) {
        val bitmapState = remember(thumbFile.absolutePath) {
            mutableStateOf(BitmapFactory.decodeFile(thumbFile.absolutePath))
        }

        DisposableEffect(thumbFile.absolutePath) {
            onDispose {
                bitmapState.value?.recycle()
                bitmapState.value = null
            }
        }

        val bitmap = bitmapState.value
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "Failed to load PDF thumbnail",
                color = Color.White
            )
        }
    } else {
        Text(
            text = "PDF thumbnail not available",
            color = Color.White
        )
    }
}

@Composable
private fun DisplayVideo(videoFile: File) {
    if (videoFile.exists()) {
        var videoView by remember { mutableStateOf<VideoView?>(null) }

        DisposableEffect(videoFile.absolutePath) {
            onDispose {
                videoView?.stopPlayback()
                videoView = null
            }
        }

        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setVideoPath(videoFile.absolutePath)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = true
                        mediaPlayer.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        false
                    }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(
            text = "Video file not found",
            color = Color.White
        )
    }
}
