package com.mindtrace.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindtrace.app.MindTraceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? MindTraceApp ?: return@launch
                ReminderScheduler.syncAll(context, app.container)
            } finally {
                pending.finish()
            }
        }
    }
}
