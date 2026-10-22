package com.mindtrace.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.BuildConfig
import com.mindtrace.app.data.AppLocale
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.TimeField
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate
import com.mindtrace.app.work.NotificationHelper
import com.mindtrace.app.work.ReminderScheduler
import kotlinx.coroutines.launch
import java.io.File

/**
 * settingsOnly = false：底部「我的」页，展示个人资料、个人记录与数据。
 * settingsOnly = true：从「我的」右上角齿轮进入的应用设置页。
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    settingsOnly: Boolean = false,
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onEditSymptoms: () -> Unit = {},
    onOpenDiagnoses: () -> Unit = {},
    onOpenSafety: () -> Unit = {},
    onOpenCrisis: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenReminderHelp: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    onOpenSupport: () -> Unit = {}
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val symptoms by viewModel.symptoms.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var notificationsAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(ReminderScheduler.exactAlarmsGranted(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDisableLock by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var showDeleteAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted && NotificationHelper.canNotify(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationHelper.canNotify(context)
        exactAlarmsAllowed = ReminderScheduler.exactAlarmsGranted(context)
    }

    fun openNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            openNotificationSettings()
        }
    }

    fun openAppDetails() {
        val intent = Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun testReminderNow() {
        if (!NotificationHelper.canNotify(context)) {
            Toast.makeText(
                context,
                translate("请先允许通知权限，再测试提醒。", language),
                Toast.LENGTH_LONG
            ).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.notificationPermissionGranted(context)
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
            return
        }
        ReminderScheduler.scheduleTest(context, 10)
        Toast.makeText(
            context,
            translate(
                "已安排 10 秒后的测试提醒。请按 Home 键退到后台；若状态栏出现闹钟图标属于正常。",
                language
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    viewModel.writeEncryptedBackup(output, backupPassword.toCharArray())
                } ?: error(translate("无法写入所选位置", language))
            }.onSuccess {
                Toast.makeText(
                    context,
                    translate("加密备份已保存。请记住密码，忘记后无法恢复。", language),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    translate("备份失败：", language) +
                        (it.message ?: translate("未知错误", language)),
                    Toast.LENGTH_LONG
                ).show()
            }
            backupPassword = ""
            busy = false
        }
    }

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    viewModel.writeCsv(output, language)
                } ?: error(translate("无法写入所选位置", language))
            }.onSuccess {
                Toast.makeText(context, translate("CSV 已导出", language), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    translate("导出失败：", language) +
                        (it.message ?: translate("未知错误", language)),
                    Toast.LENGTH_LONG
                ).show()
            }
            busy = false
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePassword = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settingsOnly) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (settingsOnly) "设置" else "我的",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        if (settingsOnly) "把边界和节奏交给你自己。"
                        else "你的资料、记录与数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!settingsOnly) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!settingsOnly) {
            item {
                SoftCard(onClick = onEditProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile?.displayName?.ifBlank { "还没有填写称呼" } ?: "还没有填写称呼",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val conditions = profile?.conditions?.split(',')
                            ?.filter { it.isNotBlank() }
                            ?.mapNotNull { key -> SymptomCatalog.conditions.firstOrNull { it.key == key }?.label }
                            .orEmpty()
                        Text(
                            conditions.joinToString("、").ifBlank { "点此填写称呼与关注方向" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }

        if (settingsOnly) {
            item {
                SoftCard {
                    SectionTitle("语言", subtitle = "界面、日期与报告语言")
                Spacer(Modifier.height(10.dp))
                SegmentedOptions(
                    options = listOf(
                        "system" to "跟随系统",
                        "zh" to "中文",
                        "en" to "English"
                    ),
                    selected = settings.language,
                    onSelect = { viewModel.setLanguage(it) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "切换后立即生效，不需要重启。复诊报告会使用当前语言。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }

        if (!settingsOnly) {
            item {
                SoftCard {
                    SettingRow(
                        icon = Icons.Rounded.Spa,
                    title = "我的症状",
                    subtitle = "已启用 ${symptoms.count { it.enabled }} 项 · 可自定义",
                    onClick = onEditSymptoms
                )
                SettingRow(
                    icon = Icons.Rounded.Description,
                    title = "诊断记录",
                    subtitle = "ICD-10 / ICD-11 / DSM-5 / 自定义",
                    onClick = onOpenDiagnoses
                )
                SettingRow(
                    icon = Icons.Rounded.Favorite,
                    title = "安全计划",
                    subtitle = "警示信号、应对方式与联系人",
                    onClick = onOpenSafety
                )
                SettingRow(
                    icon = Icons.Rounded.Warning,
                    title = "紧急支持",
                    subtitle = "12356 / 120 / 110 与信任的人",
                    onClick = onOpenCrisis
                )
            }
            }
        }

        if (settingsOnly) {
            item {
                SoftCard {
                    SectionTitle("提醒", subtitle = "通知内容默认脱敏")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("每日记录提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.dailyReminderEnabled) "每天 ${DateUtils.timeLabel(settings.dailyReminderHour, settings.dailyReminderMinute)}"
                            else "已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.dailyReminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDailyReminder(
                                enabled,
                                settings.dailyReminderHour,
                                settings.dailyReminderMinute
                            )
                        }
                    )
                }
                if (settings.dailyReminderEnabled) {
                    Spacer(Modifier.height(10.dp))
                    TimeField(
                        label = "提醒时间",
                        time = DateUtils.timeLabel(settings.dailyReminderHour, settings.dailyReminderMinute),
                        onChange = { time ->
                            val parts = time.split(':')
                            viewModel.setDailyReminder(
                                true,
                                parts.getOrNull(0)?.toIntOrNull() ?: 20,
                                parts.getOrNull(1)?.toIntOrNull() ?: 30
                            )
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("用药提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "按药物时间提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.medicationRemindersEnabled,
                        onCheckedChange = { viewModel.setMedicationReminders(it) }
                    )
                }
                val anyReminder = settings.dailyReminderEnabled || settings.medicationRemindersEnabled
                if (!notificationsAllowed) {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(
                        text = when {
                            !NotificationHelper.notificationPermissionGranted(context) ->
                                "通知权限未授予，提醒不会显示。"
                            !NotificationHelper.notificationsEnabled(context) ->
                                "系统通知已关闭，提醒不会显示。"
                            !NotificationHelper.remindersChannelEnabled(context) ->
                                "「日常提醒」通知渠道已关闭，提醒不会显示。"
                            else -> "通知不可用，提醒不会显示。"
                        },
                        icon = Icons.Rounded.Notifications,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Row {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NotificationHelper.notificationPermissionGranted(context)
                        ) {
                            TextButton(onClick = {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("请求通知权限") }
                        }
                        TextButton(onClick = { openNotificationSettings() }) { Text("打开通知设置") }
                    }
                }

                if (anyReminder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsAllowed) {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(
                        text = "系统未允许「精确提醒」。应用会改用系统闹钟方式触发，提醒仍会响，但状态栏可能出现闹钟图标；允许精确提醒后图标会消失。",
                        icon = Icons.Rounded.Warning,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    TextButton(onClick = { openExactAlarmSettings() }) { Text("去允许精确提醒") }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("测试提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "10 秒后发送一条本地通知，可退到后台观察",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { testReminderNow() }) { Text("测试") }
                }

                if (anyReminder) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "如果仍然收不到提醒：在系统设置中允许心迹自启动 / 后台运行，并把电池策略设为「不限制」。部分国产系统会额外拦截后台闹钟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { openAppDetails() },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("打开应用设置") }
                }

                TextButton(
                    onClick = onOpenReminderHelp,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("收不到提醒？查看完整帮助") }
            }
        }

        item {
            SoftCard {
                SectionTitle("隐私与安全", subtitle = "数据默认只在本机")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("应用锁", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.appLockEnabled) "已开启，进入时需要解锁" else "未开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                showDisableLock = true
                            }
                        }
                    )
                }
                if (settings.hasPin) {
                    Spacer(Modifier.height(8.dp))
                    if (biometricAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("指纹 / 面容解锁", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "PIN 仍可作为备用方式",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.biometricEnabled,
                                onCheckedChange = { viewModel.setBiometricEnabled(it) }
                            )
                        }
                    } else {
                        Text(
                            "此设备未录入指纹 / 面容，或硬件不可用。可以继续使用 PIN。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("从后台返回后自动锁定", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SegmentedOptions(
                        options = listOf(0 to "立即", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟"),
                        selected = settings.autoLockSeconds,
                        onSelect = { viewModel.setAutoLockSeconds(it) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                SettingSwitchRow(
                    title = "阻止截屏",
                    subtitle = "在最近任务中也隐藏预览",
                    checked = settings.preventScreenshots,
                    onChange = { viewModel.setPreventScreenshots(it) }
                )
                SettingSwitchRow(
                    title = "通知脱敏",
                    subtitle = "通知只显示「心迹」和一句通用提示",
                    checked = settings.stealthNotifications,
                    onChange = { viewModel.setStealthNotifications(it) }
                )
            }
            }
        }

        if (!settingsOnly) {
            item {
                SoftCard {
                    SectionTitle("数据与报告")
                Spacer(Modifier.height(6.dp))
                SettingRow(
                    icon = Icons.Rounded.Description,
                    title = "生成复诊报告",
                    subtitle = "导出 PDF 或直接分享",
                    onClick = onOpenReport
                )
                SettingRow(
                    icon = Icons.Rounded.Backup,
                    title = "导出加密备份",
                    subtitle = "含记录、药物、量表、日记、安全计划；不含 PIN 和提醒设置",
                    onClick = { showBackupPassword = true }
                )
                SettingRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "导出 CSV",
                    subtitle = "原始打卡数据，适合自行分析",
                    onClick = { createCsv.launch("MindTrace-Records-${DateUtils.todayIso()}.csv") }
                )
                SettingRow(
                    icon = Icons.Rounded.Restore,
                    title = "从备份恢复",
                    subtitle = "恢复会覆盖当前全部数据",
                    onClick = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) }
                )
                SettingRow(
                    icon = Icons.Rounded.Warning,
                    title = "删除全部数据",
                    subtitle = "不可恢复，请先导出备份",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteAll = true }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "更换手机或卸载前请先导出加密备份。卸载会销毁本机密钥，数据无法恢复；PIN 和备份密码也没有找回入口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }

        if (settingsOnly) {
            item {
                SoftCard {
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = "关于心迹",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME} · 医疗免责声明",
                    onClick = onOpenAbout
                )
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = "隐私说明",
                    subtitle = "无网络权限、加密存储与数据边界",
                    onClick = onOpenPrivacy
                )
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "使用条款",
                    subtitle = "医疗边界、数据责任与紧急资源",
                    onClick = onOpenTerms
                )
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "问题反馈与诊断",
                    subtitle = "复制版本与权限状态，不包含病情内容",
                    onClick = onOpenSupport
                )
            }
            }
        }

        item {
            Text(
                "心迹不是医疗器械，不提供诊断或治疗建议。紧急情况请联系 120 / 110 或 12356。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            }
        )
    }

    if (showDisableLock) {
        ConfirmDialog(
            title = "关闭应用锁？",
            text = "关闭后，任何拿到手机的人都能直接打开心迹。数据仍然加密，但不再有界面门禁。",
            confirmLabel = "关闭",
            destructive = true,
            onConfirm = {
                viewModel.disableAppLock()
                showDisableLock = false
            },
            onDismiss = { showDisableLock = false }
        )
    }

    if (showBackupPassword) {
        PasswordDialog(
            title = "设置备份密码",
            description = "密码不会保存在应用里。忘记密码将无法恢复这份备份。",
            requireConfirm = true,
            onDismiss = { showBackupPassword = false },
            onConfirm = { password ->
                backupPassword = password
                showBackupPassword = false
                createBackup.launch("MindTrace-Backup-${DateUtils.todayIso()}.mtrbackup")
            }
        )
    }

    if (showRestorePassword && pendingRestoreUri != null) {
        PasswordDialog(
            title = "输入备份密码",
            description = "恢复会覆盖当前全部记录。建议先导出当前数据的备份。",
            requireConfirm = false,
            onDismiss = {
                showRestorePassword = false
                pendingRestoreUri = null
            },
            onConfirm = { password ->
                val uri = pendingRestoreUri
                showRestorePassword = false
                pendingRestoreUri = null
                if (uri == null) return@PasswordDialog
                scope.launch {
                    busy = true
                    val result = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            viewModel.restoreEncryptedBackup(input, password.toCharArray()).getOrThrow()
                        } ?: error(translate("无法读取备份文件", language))
                    }
                    result.onSuccess {
                        Toast.makeText(context, translate("恢复完成", language), Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            translate("恢复失败：", language) +
                                (it.message ?: translate("密码错误或文件损坏", language)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    busy = false
                }
            }
        )
    }

    if (showDeleteAll) {
        ConfirmDialog(
            title = "删除全部数据？",
            text = "这会删除所有记录、药物、量表、日记、安全计划和设置，并且无法恢复。建议先导出加密备份。",
            confirmLabel = "全部删除",
            destructive = true,
            onConfirm = {
                showDeleteAll = false
                viewModel.eraseAllData {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }
            },
            onDismiss = { showDeleteAll = false }
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..6 && pin.all(Char::isDigit) && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置应用锁 PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("4–6 位数字 PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(6) },
                    label = { Text("再输入一次") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = confirm.isNotEmpty() && pin != confirm,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "没有找回入口，请记住这个 PIN。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(pin) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!requireConfirm || password == confirm)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（至少 8 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("再次输入密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirm.isNotEmpty() && password != confirm,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
