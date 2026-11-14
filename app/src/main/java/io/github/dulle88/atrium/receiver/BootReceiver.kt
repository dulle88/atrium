package io.github.dulle88.atrium.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.dulle88.atrium.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // The system already started us as HOME. A second startActivity at boot deadlocks system_server on the TX1-S
            // (vendor activity controller: window-manager lock vs activity-manager lock), freezing the whole box.
            if (MainActivity.created) return
            Log.d("BootReceiver", "Boot completed, starting MainActivity")

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(activityIntent)
        }
    }
}
