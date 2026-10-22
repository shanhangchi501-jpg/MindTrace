package com.mindtrace.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindtrace.app.MindTraceApp
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.displayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? MindTraceApp
            try {
                if (app != null) {
                    val settings = app.container.settings.snapshot()
                    when (type) {
                        ReminderScheduler.TYPE_CHECK_IN -> {
                            if (settings.dailyReminderEnabled) {
                                NotificationHelper.showCheckInReminder(context, settings)
                                ReminderScheduler.scheduleNextCheckIn(context, settings)
                            }
                        }
                        ReminderScheduler.TYPE_MEDICATION -> {
                            val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME)
                            if (time != null && settings.medicationRemindersEnabled) {
                                val today = DateUtils.todayIso()
                                val meds = app.container.medicationRepository.getActive()
                                    .filter {
                                        it.active && !it.asNeeded &&
                                            it.startDate <= today &&
                                            (it.endDate == null || it.endDate >= today) &&
                                            it.times.split(',').map(String::trim).contains(time)
                                    }
                                if (meds.isNotEmpty()) {
                                    NotificationHelper.showMedicationReminder(
                                        context,
                                        time,
                                        meds.map { it.displayLabel() },
                                        settings
                                    )
                                }
                                ReminderScheduler.scheduleNextMedication(context, time)
                            }
                        }
                        ReminderScheduler.TYPE_TEST -> {
                            NotificationHelper.showTestReminder(context)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
