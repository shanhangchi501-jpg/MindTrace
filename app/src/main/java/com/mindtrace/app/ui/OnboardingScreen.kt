package com.mindtrace.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.AppLocale
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.ui.components.AppMark
import com.mindtrace.app.ui.components.CheckRow
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.TimeField
import com.mindtrace.app.ui.components.WarmBackground
import com.mindtrace.app.ui.theme.MoodColors
import com.mindtrace.app.work.ReminderScheduler

private const val PAGE_COUNT = 4

@Composable
fun OnboardingScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val english = AppLocale.fromCode(settings.language).isEnglish

    var disclaimerAccepted by remember { mutableStateOf(false) }
    var selectedConditions by remember { mutableStateOf(emptyList<String>()) }
    var selectedSymptoms by remember {
        mutableStateOf(SymptomCatalog.suggestedFor(listOf("unsure")))
    }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderHour by remember { mutableStateOf(20) }
    var reminderMinute by remember { mutableStateOf(30) }
    var medicationReminderEnabled by remember { mutableStateOf(true) }
    var lockEnabled by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(false) }
    var exactAlarmsAllowed by remember { mutableStateOf(ReminderScheduler.exactAlarmsGranted(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactAlarmsAllowed = ReminderScheduler.exactAlarmsGranted(context)
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val pinValid = pin.length in 4..6 && pin.all(Char::isDigit)
    val pinMatches = pinValid && pin == pinConfirm
    val canContinue = when (pagerState.currentPage) {
        0 -> disclaimerAccepted
        1 -> selectedSymptoms.isNotEmpty()
        2 -> true
        else -> !lockEnabled || pinMatches
    }

    fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (reminderEnabled || medicationReminderEnabled)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun finish() {
        requestNotificationIfNeeded()
        viewModel.completeOnboarding(
            displayName = "",
            conditions = selectedConditions.ifEmpty { listOf("unsure") },
            enabledSymptomIds = selectedSymptoms,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            pin = if (lockEnabled) pin else null,
            biometric = lockEnabled && biometricEnabled
        )
    }

    WarmBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    viewModel.setLanguage(if (english) "zh" else "en")
                }) { Text(if (english) "中文" else "EN") }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PAGE_COUNT) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 20.dp else 7.dp, 7.dp)
                                .background(
                                    if (index <= pagerState.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pagerState.currentPage + 1} / $PAGE_COUNT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        accepted = disclaimerAccepted,
                        onAcceptedChange = { disclaimerAccepted = it }
                    )
                    1 -> ConditionPage(
                        selectedConditions = selectedConditions,
                        selectedSymptoms = selectedSymptoms,
                        onToggleCondition = { key ->
                            val wasSelected = key in selectedConditions
                            selectedConditions = if (wasSelected) {
                                selectedConditions - key
                            } else {
                                selectedConditions + key
                            }
                            if (!wasSelected) {
                                selectedSymptoms = selectedSymptoms + SymptomCatalog.suggestedFor(listOf(key))
                            }
                        },
                        onToggleSymptom = { id ->
                            selectedSymptoms = if (id in selectedSymptoms) {
                                selectedSymptoms - id
                            } else {
                                selectedSymptoms + id
                            }
                        }
                    )
                    2 -> ReminderPage(
                        reminderEnabled = reminderEnabled,
                        reminderTime = String.format("%02d:%02d", reminderHour, reminderMinute),
                        medicationReminderEnabled = medicationReminderEnabled,
                        exactAlarmsAllowed = exactAlarmsAllowed,
                        onRequestExactAlarms = { openExactAlarmSettings() },
                        onReminderChange = { reminderEnabled = it },
                        onReminderTimeChange = { time ->
                            val parts = time.split(':')
                            reminderHour = parts.getOrNull(0)?.toIntOrNull() ?: 20
                            reminderMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30
                        },
                        onMedicationReminderChange = { medicationReminderEnabled = it }
                    )
                    else -> LockPage(
                        lockEnabled = lockEnabled,
                        pin = pin,
                        pinConfirm = pinConfirm,
                        biometricEnabled = biometricEnabled,
                        biometricAvailable = biometricAvailable,
                        pinValid = pinValid,
                        pinMatches = pinMatches,
                        onLockChange = { lockEnabled = it },
                        onPinChange = { pin = it.filter(Char::isDigit).take(6) },
                        onPinConfirmChange = { pinConfirm = it.filter(Char::isDigit).take(6) },
                        onBiometricChange = { biometricEnabled = it }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) { Text("上一步") }
                    Spacer(Modifier.width(10.dp))
                }
                PrimaryActionButton(
                    text = if (pagerState.currentPage == PAGE_COUNT - 1) "开始使用" else "继续",
                    modifier = Modifier.weight(1f),
                    enabled = canContinue,
                    onClick = {
                        if (pagerState.currentPage == PAGE_COUNT - 1) {
                            finish()
                        } else {
                            if (pagerState.currentPage == 2) requestNotificationIfNeeded()
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WelcomePage(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AppMark(size = 72)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "心迹",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "给自己留一条线索",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Text(
                text = "记录情绪、睡眠、症状与用药，帮你在复诊时更完整地讲述自己的状态。它不催你变好，只陪你把起伏留下来。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            SoftCard {
                FeatureLine(Icons.Rounded.WifiOff, "完全离线", "不申请网络权限，数据不会离开这台手机。")
                Spacer(Modifier.height(14.dp))
                FeatureLine(Icons.Rounded.Shield, "加密保存", "数据库整体加密，密钥由系统安全区保管。")
                Spacer(Modifier.height(14.dp))
                FeatureLine(Icons.Rounded.Lock, "只属于你", "应用锁、防截屏与脱敏通知，都由你决定。")
            }
        }
        item {
            InfoBanner(
                text = "心迹是自我记录工具，不提供诊断或治疗建议，也不能替代医生。如果你正处于危险中，请联系 120 / 110 或心理援助热线 12356。",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                CheckRow(
                    text = "我已了解：心迹不是医疗器械，紧急情况请寻求专业帮助。",
                    checked = accepted,
                    onToggle = onAcceptedChange
                )
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun FeatureLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionPage(
    selectedConditions: List<String>,
    selectedSymptoms: Set<String>,
    onToggleCondition: (String) -> Unit,
    onToggleSymptom: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("最近在经历什么？", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "可多选，也可以不选。之后随时能在设置里调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SymptomCatalog.conditions.forEach { condition ->
                    Pill(
                        text = condition.label,
                        selected = condition.key in selectedConditions,
                        onClick = { onToggleCondition(condition.key) }
                    )
                }
            }
        }
        item {
            Column {
                Text("想记录哪些感受？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "已根据上面的选择为你勾选了一些。也可以只留几个最在意的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SymptomCatalog.symptoms.groupBy { it.category }.forEach { (category, items) ->
            item {
                Column {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { symptom ->
                            Pill(
                                text = symptom.name,
                                selected = symptom.id in selectedSymptoms,
                                onClick = { onToggleSymptom(symptom.id) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "选了 ${selectedSymptoms.size} 项。打卡时不必每次都填满，愿意记多少就记多少。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ReminderPage(
    reminderEnabled: Boolean,
    reminderTime: String,
    medicationReminderEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestExactAlarms: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onReminderTimeChange: (String) -> Unit,
    onMedicationReminderChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("留一点固定的时间", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "提醒只是轻轻的提示，错过也不会被责备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SoftCard {
                ToggleLine(
                    title = "每日记录提醒",
                    subtitle = "在选定的时间提醒你写今天的记录",
                    checked = reminderEnabled,
                    onChange = onReminderChange
                )
                if (reminderEnabled) {
                    Spacer(Modifier.height(14.dp))
                    TimeField(
                        label = "提醒时间",
                        time = reminderTime,
                        onChange = onReminderTimeChange
                    )
                }
            }
        }
        item {
            SoftCard {
                ToggleLine(
                    title = "用药提醒",
                    subtitle = "按药物时间提醒，内容可选脱敏",
                    checked = medicationReminderEnabled,
                    onChange = onMedicationReminderChange
                )
            }
        }
        if ((reminderEnabled || medicationReminderEnabled) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsAllowed
        ) {
            item {
                InfoBanner(
                    text = "没有「精确提醒」权限时，应用会改用系统闹钟方式触发，提醒仍会响；状态栏可能出现闹钟图标。允许权限后图标会消失。",
                    icon = Icons.Rounded.Lock,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRequestExactAlarms) { Text("去允许精确提醒") }
            }
        }
        item {
            InfoBanner(
                text = "提醒依赖系统通知权限。首次使用时系统会询问；如果拒绝了，可以在系统设置中重新开启。部分国产系统还需要允许自启动或关闭电池优化。",
                icon = Icons.Rounded.CheckCircle
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LockPage(
    lockEnabled: Boolean,
    pin: String,
    pinConfirm: String,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    pinValid: Boolean,
    pinMatches: Boolean,
    onLockChange: (Boolean) -> Unit,
    onPinChange: (String) -> Unit,
    onPinConfirmChange: (String) -> Unit,
    onBiometricChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("最后，加一道门", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "应用锁可以防止别人随手打开。它不是数据加密的唯一防线，但能让记录更安心。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SoftCard {
                ToggleLine(
                    title = "启用应用锁",
                    subtitle = "每次进入或从后台返回时需要解锁",
                    checked = lockEnabled,
                    onChange = onLockChange
                )
                if (lockEnabled) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = onPinChange,
                        label = { Text("设置 4–6 位数字 PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = onPinConfirmChange,
                        label = { Text("再输入一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinConfirm.isNotEmpty() && !pinMatches,
                        supportingText = {
                            if (pinConfirm.isNotEmpty() && !pinMatches) {
                                Text("两次输入不一致")
                            } else if (pin.isNotEmpty() && !pinValid) {
                                Text("请输入 4–6 位数字")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (biometricAvailable) {
                        Spacer(Modifier.height(8.dp))
                        ToggleLine(
                            title = "使用指纹 / 面容解锁",
                            subtitle = "PIN 仍然可以作为备用方式",
                            checked = biometricEnabled && pinMatches,
                            onChange = { if (pinMatches) onBiometricChange(it) }
                        )
                    }
                }
            }
        }
        item {
            InfoBanner(
                text = "请记住 PIN。应用没有服务器，也没有找回入口；如果忘记 PIN，只能清除应用数据，记录会一并消失。",
                tint = MaterialTheme.colorScheme.error
            )
        }
        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text("准备好了。", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "你不需要一次就把所有事情说清楚。今天愿意记一点，就已经足够。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
