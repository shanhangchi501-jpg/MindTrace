package com.mindtrace.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mindtrace.app.MainActivity
import com.mindtrace.app.data.AppContainer
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.data.UiSettings
import java.time.ZonedDateTime

object ReminderScheduler {

    const val ACTION_REMINDER = "com.mindtrace.app.REMINDER"
    const val EXTRA_TYPE = "type"
    const val EXTRA_TIME = "time"
    const val TYPE_CHECK_IN = "checkin"
    const val TYPE_MEDICATION = "medication"
    const val TYPE_TEST = "test"

    private const val CHECK_IN_REQUEST = 1001
    private const val TEST_REQUEST = 1901
    // 没有精确闹钟权限时的回退窗口。窗口越短越准时，但系统仍可能批量延迟。
    private const val WINDOW_MILLIS = 60 * 1000L
    private const val PREFS = "mindtrace_alarms"
    private const val KEY_MED_TIMES = "scheduled_med_times"

    fun sync(context: Context, settings: UiSettings, medications: List<MedicationEntity>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        if (settings.dailyReminderEnabled) {
            scheduleCheckIn(context, alarmManager, settings.dailyReminderHour, settings.dailyReminderMinute)
        } else {
            alarmManager.cancel(checkInIntent(context))
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldTimes = prefs.getStringSet(KEY_MED_TIMES, emptySet()).orEmpty().toSet()
        val newTimes = if (settings.medicationRemindersEnabled) {
            medications.filter { it.active && !it.asNeeded }
                .flatMap { it.times.split(',').map(String::trim).filter(String::isNotBlank) }
                .distinct()
        } else {
            emptySet()
        }

        (oldTimes - newTimes).forEach { time ->
            alarmManager.cancel(medicationIntent(context, time))
        }
        newTimes.forEach { time ->
            scheduleMedication(context, alarmManager, time)
        }
        prefs.edit().putStringSet(KEY_MED_TIMES, newTimes.toMutableSet()).apply()
    }

    suspend fun syncAll(context: Context, container: AppContainer) {
        val medications = runCatching { container.medicationRepository.getActive() }.getOrDefault(emptyList())
        sync(context, container.settings.snapshot(), medications)
    }

    fun scheduleCheckIn(context: Context, alarmManager: AlarmManager, hour: Int, minute: Int) {
        val trigger = nextTriggerMillis(hour, minute)
        scheduleAlarm(context, alarmManager, trigger, checkInIntent(context))
    }

    fun scheduleMedication(context: Context, alarmManager: AlarmManager, time: String) {
        val (hour, minute) = parseTime(time) ?: return
        val trigger = nextTriggerMillis(hour, minute)
        scheduleAlarm(context, alarmManager, trigger, medicationIntent(context, time))
    }

    fun exactAlarmsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** 安排一条 10 秒后的测试提醒，帮助用户确认通知与闹钟权限是否正常。 */
    fun scheduleTest(context: Context, seconds: Long = 10L) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = System.currentTimeMillis() + seconds.coerceAtLeast(1L) * 1000L
        scheduleAlarm(context, manager, trigger, testIntent(context))
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (exactAlarmsGranted(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            } catch (_: SecurityException) {
                // 权限在检查后被撤销，继续走下面的系统闹钟保底。
            }
        }

        // 没有精确闹钟权限时，用系统闹钟方式触发。
        // setAlarmClock 不受 SCHEDULE_EXACT_ALARM 限制，能在 Doze 和多数
        // 定制系统里可靠唤醒；代价是状态栏会出现一个闹钟图标。
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, alarmClockShowIntent(context)),
                pendingIntent
            )
            return
        } catch (_: SecurityException) {
            // 极少数系统仍拒绝，退回到窗口闹钟。
        }
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            WINDOW_MILLIS,
            pendingIntent
        )
    }

    private fun alarmClockShowIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            7101,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleNextCheckIn(context: Context, settings: UiSettings) {
        if (!settings.dailyReminderEnabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        scheduleCheckIn(context, alarmManager, settings.dailyReminderHour, settings.dailyReminderMinute)
    }

    fun scheduleNextMedication(context: Context, time: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        scheduleMedication(context, alarmManager, time)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(checkInIntent(context))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_MED_TIMES, emptySet()).orEmpty().forEach { time ->
            alarmManager.cancel(medicationIntent(context, time))
        }
        prefs.edit().remove(KEY_MED_TIMES).apply()
    }

    private fun checkInIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            CHECK_IN_REQUEST,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_TYPE, TYPE_CHECK_IN)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun testIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TEST_REQUEST,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_TYPE, TYPE_TEST)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun medicationIntent(context: Context, time: String): PendingIntent {
        val request = 2000 + (time.hashCode() and 0x7fffffff) % 100000
        return PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_TYPE, TYPE_MEDICATION)
                putExtra(EXTRA_TIME, time)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    fun parseTime(time: String): Pair<Int, Int>? {
        val parts = time.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun todayLabel(): String = DateUtils.todayIso()
}
