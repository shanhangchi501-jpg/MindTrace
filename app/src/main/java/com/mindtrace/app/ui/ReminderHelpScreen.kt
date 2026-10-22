package com.mindtrace.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SecondaryActionButton
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate
import com.mindtrace.app.work.NotificationHelper
import com.mindtrace.app.work.ReminderScheduler

@Composable
fun ReminderHelpScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissionGranted = NotificationHelper.notificationPermissionGranted(context)
    val systemNotifications = NotificationHelper.notificationsEnabled(context)
    val channelEnabled = NotificationHelper.remindersChannelEnabled(context)
    val exactAlarm = ReminderScheduler.exactAlarmsGranted(context)

    fun toast(message: String) {
        Toast.makeText(context, translate(message, language), Toast.LENGTH_LONG).show()
    }

    fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openAppDetails()
        }
    }

    fun testReminder() {
        if (!NotificationHelper.canNotify(context)) {
            toast("通知不可用，请先开启通知权限和「日常提醒」通知渠道。")
            openNotificationSettings()
            return
        }
        ReminderScheduler.scheduleTest(context, 10)
        toast("已安排 10 秒后的测试提醒。请按 Home 退到后台；出现闹钟图标属于正常。")
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
                title = "提醒帮助",
                subtitle = "如果提醒不准时或收不到，从这里开始排查",
                onBack = onBack
            )
        }
        item {
            SoftCard {
                SectionTitle("当前状态")
                Spacer(Modifier.height(8.dp))
                StatusLine("通知权限", if (permissionGranted) "已允许" else "未允许")
                StatusLine("系统通知", if (systemNotifications) "已开启" else "已关闭")
                StatusLine("「日常提醒」渠道", if (channelEnabled) "已开启" else "已关闭")
                StatusLine(
                    "精确提醒",
                    when {
                        exactAlarm -> "已允许"
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "系统版本不需要"
                        else -> "未允许"
                    }
                )
            }
        }
        item {
            PrimaryActionButton(text = "发送 10 秒测试提醒", onClick = { testReminder() })
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(text = "打开通知设置", onClick = { openNotificationSettings() })
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { openAppDetails() }) { Text("打开应用设置（自启动 / 电池）") }
        }
        item {
            InfoBanner(
                text = "提醒依赖系统通知和后台调度。若测试提醒能收到，但实际提醒不响，通常是系统的自启动、后台运行或电池策略拦截。"
            )
        }
        item {
            HelpCard(
                title = "Android 13 及以上",
                steps = listOf(
                    "允许心迹发送通知。",
                    "如果出现「闹钟和提醒」提示，允许精确提醒；若未允许，应用会改用系统闹钟方式，状态栏可能出现闹钟图标。",
                    "在电池设置中把心迹设为「不受限制」。",
                    "点上面的「发送 10 秒测试提醒」，然后按 Home 退到后台验证。"
                )
            )
        }
        item {
            HelpCard(
                title = "小米 / Redmi / HyperOS",
                steps = listOf(
                    "设置 → 应用设置 → 应用管理 → 心迹 → 权限 → 通知：全部允许。",
                    "设置 → 应用设置 → 应用管理 → 心迹 → 自启动：开启。",
                    "设置 → 应用设置 → 应用管理 → 心迹 → 省电策略：设为「无限制」。",
                    "在最近任务中长按心迹卡片并锁定，避免一键清理时被划掉。"
                )
            )
        }
        item {
            HelpCard(
                title = "华为 / 荣耀",
                steps = listOf(
                    "设置 → 应用和服务 → 应用启动管理 → 心迹：关闭自动管理，手动允许自启动、关联启动、后台活动。",
                    "设置 → 电池 → 更多电池设置 → 休眠时始终保持网络连接：开启。",
                    "设置 → 电池 → 应用耗电管理 → 心迹：允许后台活动。",
                    "在最近任务中锁定心迹，避免被清理。"
                )
            )
        }
        item {
            HelpCard(
                title = "OPPO / 一加 / realme",
                steps = listOf(
                    "设置 → 电池 → 应用耗电管理 → 心迹：允许后台运行。",
                    "设置 → 应用管理 → 心迹 → 自启动：开启。",
                    "关闭「智能省电」或把心迹加入白名单。",
                    "在最近任务中锁定心迹。"
                )
            )
        }
        item {
            HelpCard(
                title = "vivo / iQOO",
                steps = listOf(
                    "设置 → 电池 → 后台耗电管理 → 心迹：允许后台高耗电。",
                    "设置 → 应用与权限 → 权限管理 → 自启动：允许心迹。",
                    "设置 → 电池 → 省电模式：将心迹加入白名单。"
                )
            )
        }
        item {
            HelpCard(
                title = "三星 / 原生 Android",
                steps = listOf(
                    "设置 → 应用 → 心迹 → 电池：设为「不受限制」。",
                    "关闭系统省电模式，或把心迹加入「未监视的应用」。",
                    "允许通知，并在通知渠道中开启「日常提醒」。"
                )
            )
        }
        item {
            SoftCard {
                SectionTitle("不要做的事")
                Spacer(Modifier.height(6.dp))
                Text(
                    "不要在最近任务里把心迹划掉，也不要在系统设置中「强行停止」。强行停止会清除已安排的闹钟，提醒需要重新打开应用后才会恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "提醒可能受系统限制，不能作为唯一的安全监护。如果你正处于危险中，请直接拨打 120 / 110 或心理援助热线 12356；不在中国大陆请拨打当地急救电话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HelpCard(title: String, steps: List<String>) {
    SoftCard {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
