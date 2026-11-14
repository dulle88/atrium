package io.github.dulle88.atrium

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import android.view.KeyEvent
import io.github.dulle88.atrium.ui.screens.LobbyScreen
import io.github.dulle88.atrium.ui.screens.LobbyViewModel
import io.github.dulle88.atrium.ui.theme.AtriumTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LobbyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        created = true

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable immersive full-screen mode
        setupImmersiveMode()

        // Kiosk: BACK never leaves the app; it only closes the portal overlay. A dispatcher callback
        // is used because with targetSdk 33+ predictive back the activity no longer sees KEYCODE_BACK.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = viewModel.hidePortalInfo()
        })

        setContent {
            AtriumTheme {
                val mediaItems by viewModel.mediaItems.collectAsState()
                val usbMediaItems by viewModel.usbMediaItems.collectAsState()
                val nowMs by viewModel.nowMs.collectAsState()
                val notices by viewModel.notices.collectAsState()
                val slideshowOrder by viewModel.slideshowOrder.collectAsState()
                val slideshowSeconds by viewModel.slideshowSeconds.collectAsState()
                val phones by viewModel.phones.collectAsState()
                val portalState by viewModel.portalState.collectAsState()
                val showPortalInfo by viewModel.showPortalInfo.collectAsState()

                LobbyScreen(
                    mediaItems = mediaItems,
                    usbMediaItems = usbMediaItems,
                    nowMs = nowMs,
                    notices = notices,
                    slideshowOrder = slideshowOrder,
                    slideshowSeconds = slideshowSeconds,
                    phones = phones,
                    portalState = portalState,
                    showPortalInfo = showPortalInfo,
                    onClockClick = { viewModel.togglePortalInfo() }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) viewModel.togglePortalInfo()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        /** True once the activity exists in this process; BootReceiver reads it to avoid a redundant startActivity. */
        @Volatile var created = false
    }
}
