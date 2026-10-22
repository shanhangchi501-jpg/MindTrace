package com.mindtrace.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindtrace.app.BuildConfig
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate
import com.mindtrace.app.work.NotificationHelper
import com.mindtrace.app.work.ReminderScheduler

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val english = language.isEnglish
    val diagnostics = remember(context, english) { buildDiagnostics(context, english) }

    fun copy() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText(
                if (english) "MindTrace diagnostics" else "心迹诊断信息",
                diagnostics
            )
        )
        Toast.makeText(
            context,
            translate("诊断信息已复制。请只粘贴这些信息，不要附带真实病情内容。", language),
            Toast.LENGTH_LONG
        ).show()
    }

    fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "问题反馈",
                subtitle = "帮助我更快定位你遇到的问题",
                onBack = onBack
            )
        }
        item {
            SoftCard {
                SectionTitle("反馈时请附上")
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. 问题发生在哪个页面\n" +
                        "2. 点击了哪些按钮、先后顺序\n" +
                        "3. 预期看到什么，实际看到什么\n" +
                        "4. 截图（请先遮住姓名、药名、日记等真实内容）\n" +
                        "5. 点下方按钮复制的诊断信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            PrimaryActionButton(text = "复制诊断信息", onClick = { copy() })
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { openAppDetails() }) { Text("打开应用设置") }
        }
        item {
            SoftCard {
                SectionTitle("将复制的诊断信息预览")
                Spacer(Modifier.height(6.dp))
                Text(
                    diagnostics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            InfoBanner(
                text = "诊断信息只包含版本、系统型号和通知/提醒权限状态，不包含你的记录、药名、日记或联系人。请不要在反馈中发送真实病情数据。"
            )
        }
        item {
            Text(
                "反馈渠道：请通过你获取心迹的平台（如 B 站、小红书或 GitHub）私信/评论作者，并附上上面的诊断信息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

private fun buildDiagnostics(context: Context, english: Boolean): String {
    val allowed = if (english) "Allowed" else "已允许"
    val notAllowed = if (english) "Not allowed" else "未允许"
    val on = if (english) "On" else "已开启"
    val off = if (english) "Off" else "已关闭"
    val exact = if (ReminderScheduler.exactAlarmsGranted(context)) allowed else notAllowed
    val permission = if (NotificationHelper.notificationPermissionGranted(context)) allowed else notAllowed
    val notifications = if (NotificationHelper.notificationsEnabled(context)) on else off
    val channel = if (NotificationHelper.remindersChannelEnabled(context)) on else off
    return buildString {
        if (english) {
            appendLine("MindTrace ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Notification permission: $permission")
            appendLine("System notifications: $notifications")
            appendLine("Daily reminder channel: $channel")
            appendLine("Exact alarms: $exact")
            appendLine("Locale: ${java.util.Locale.getDefault()}")
        } else {
            appendLine("心迹 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("包名：${context.packageName}")
            appendLine("Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("通知权限：$permission")
            appendLine("系统通知：$notifications")
            appendLine("日常提醒渠道：$channel")
            appendLine("精确提醒：$exact")
            appendLine("Locale：${java.util.Locale.getDefault()}")
        }
    }.trim()
}
