package io.github.dulle88.atrium.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.dulle88.atrium.portal.PortalInfo
import io.github.dulle88.atrium.portal.PortalState

@Composable
fun PortalInfoOverlay(state: PortalState, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        // Designed for 1280x720 dp; scaled down on smaller screens so the card stays inside the TV safe area.
        val s = minOf(maxWidth / 1280.dp, maxHeight / 720.dp).coerceIn(0.5f, 1f)
        val note = Modifier.widthIn(max = maxWidth * 0.7f)
        Column(
            Modifier.background(Color(0xFF141B33), RoundedCornerShape(20.dp)).padding(40.dp * s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp * s)
        ) {
            when (state) {
                PortalState.NoNetwork -> {
                    Text("Not connected to a network", color = Color.White, fontSize = 32.sp * s)
                    Text("Join the TV to your iPhone hotspot, then press OK on the clock again.", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp * s, textAlign = TextAlign.Center, modifier = note)
                }
                PortalState.NotRunning -> {
                    Text("Portal is not running", color = Color.White, fontSize = 32.sp * s)
                    Text("The TV retries every 30 seconds. If this persists, restart the TV box.", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp * s, textAlign = TextAlign.Center, modifier = note)
                }
                is PortalState.Running -> {
                    val info = state.info
                    val wifi = info.wifi
                    if (wifi == null) {
                        PortalStep(
                            info, s,
                            if (info.onHotspot) "Join the TV's hotspot in your phone's Wi-Fi settings, then scan the code or open the address"
                            else "Open the address in Safari on your iPhone"
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp * s), verticalAlignment = Alignment.Top) {
                            Step(
                                1, "Join the TV's Wi-Fi", wifi.qrPayload, s,
                                wifi.ssid, "Password  ${wifi.passphrase}", "Scan with the phone camera, tap Join"
                            )
                            Box(Modifier.width(2.dp).height(430.dp * s).background(Color.White.copy(alpha = 0.15f)))
                            Step(
                                2, "Open the portal", info.url, s,
                                info.url, "PIN  ${info.pin}", "Scan, or type the address in Safari"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}

@Composable
private fun Step(n: Int, title: String, qrText: String, s: Float, line1: String, line2: String, note: String) {
    val qr = remember(qrText) { qrBitmap(qrText) }
    Column(Modifier.width(420.dp * s), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp * s)) {
        Box(Modifier.size(44.dp * s).background(Color(0xFFF0A45D), CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = Color(0xFF141B33), fontSize = 26.sp * s, fontWeight = FontWeight.Bold)
        }
        Text(title, color = Color.White, fontSize = 26.sp * s, fontWeight = FontWeight.Bold)
        Image(qr.asImageBitmap(), contentDescription = "QR code: $title", modifier = Modifier.size(240.dp * s))
        Text(line1, color = Color.White, fontSize = 25.sp * s, textAlign = TextAlign.Center)
        Text(line2, color = Color(0xFFF0A45D), fontSize = 25.sp * s, textAlign = TextAlign.Center)
        Text(note, color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp * s, textAlign = TextAlign.Center)
    }
}

/** Single-card layout used when the TV's own Wi-Fi is not up (portal reached over the existing network). */
@Composable
private fun PortalStep(info: PortalInfo, s: Float, note: String) {
    val qr = remember(info.url) { qrBitmap(info.url) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp * s)) {
        Image(qr.asImageBitmap(), contentDescription = "QR code for ${info.url}", modifier = Modifier.size(240.dp * s))
        Text(info.url, color = Color.White, fontSize = 36.sp * s)
        Text("PIN  ${info.pin}", color = Color(0xFFF0A45D), fontSize = 36.sp * s)
        Text(note, color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp * s, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp * s))
    }
}
