package com.mindtrace.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mindtrace.app.MainActivity
import com.mindtrace.app.R
import com.mindtrace.app.data.AppLocale
import com.mindtrace.app.data.UiSettings
import com.mindtrace.app.data.displayLabel
import java.util.Locale

object NotificationHelper {

    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_SAFETY = "safety"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val english = AppLocale.current.isEnglish
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            if (english) "Check-in reminders" else context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = if (english) {
                "Daily check-in and medication reminders; can be turned off in Settings"
            } else {
                context.getString(R.string.notification_channel_reminders_desc)
            }
            enableVibration(true)
        }
        val safety = NotificationChannel(
            CHANNEL_SAFETY,
            if (english) "Safety reminders" else context.getString(R.string.notification_channel_safety),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = if (english) {
                "Safety plan and visit-related reminders"
            } else {
                context.getString(R.string.notification_channel_safety_desc)
            }
        }
        manager.createNotificationChannels(listOf(reminders, safety))
    }

    fun showCheckInReminder(context: Context, settings: UiSettings) {
        if (!canNotify(context)) return
        val english = AppLocale.fromCode(settings.language).isEnglish
        val title = if (settings.stealthNotifications) {
            if (english) "MindTrace" else "心迹"
        } else {
            if (english) "Today's check-in" else "今天的记录"
        }
        val body = if (settings.stealthNotifications) {
            if (english) "A gentle reminder to check in. A minute is enough, and it's only for you."
            else "该记录一下了。花一分钟，也只属于你。"
        } else {
            if (english) "Take a minute to note today's mood, sleep and medication."
            else "今天的情绪、睡眠和用药，花一分钟记下来吧。"
        }
        notify(context, 1001, title, body, destination = "checkin", settings)
    }

    fun showTestReminder(context: Context) {
        if (!canNotify(context)) return
        val english = AppLocale.current.isEnglish
        val title = if (english) "MindTrace · Reminder test" else "心迹 · 提醒测试"
        val body = if (english) {
            "If you can see this notification, notification permission and alarm scheduling are working."
        } else {
            "如果你看到这条通知，说明通知权限和闹钟调度正常。"
        }
        notify(context, 3001, title, body, destination = "reminders", settings = null)
    }

    fun showMedicationReminder(context: Context, time: String, names: List<String>, settings: UiSettings) {
        if (!canNotify(context)) return
        val english = AppLocale.fromCode(settings.language).isEnglish
        val title = if (settings.stealthNotifications) {
            if (english) "MindTrace" else "心迹"
        } else {
            if (english) "Medication reminder · $time" else "用药提醒 · $time"
        }
        val body = if (settings.stealthNotifications) {
            if (english) "It's time — remember to take care of yourself."
            else "到时间了，记得照顾自己。"
        } else if (names.isEmpty()) {
            if (english) "It's time for your medication. Take a moment to note how today is going."
            else "到服药时间了，记一下今天的情况吧。"
        } else {
            if (english) "Time for your medication: ${names.joinToString(", ")}"
            else "到服药时间：${names.joinToString("、")}"
        }
        val id = 2000 + time.hashCode().let { if (it < 0) -it else it } % 1000
        notify(context, id, title, body, destination = "medications", settings)
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        body: String,
        destination: String,
        settings: UiSettings? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", destination)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setColor(0xFF6B8F7B.toInt())
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun remindersChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(CHANNEL_REMINDERS) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canNotify(context: Context): Boolean =
        notificationPermissionGranted(context) &&
            notificationsEnabled(context) &&
            remindersChannelEnabled(context)

    fun timeLabel(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)
}
