package com.mindtrace.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.AdherenceSummary
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DoseSlot
import com.mindtrace.app.data.DoseStatus
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.data.MedicationLogEntity
import com.mindtrace.app.data.displayLabel
import kotlinx.coroutines.delay
import com.mindtrace.app.ui.components.AdherenceRing
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.components.MetricTile
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate

@Composable
fun MedicationsScreen(
    viewModel: AppViewModel,
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit,
    onBackdatedCheckIn: (String) -> Unit
) {
    val medications by viewModel.allMedications.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val today = DateUtils.todayIso()
    val english = LocalAppLanguage.current.isEnglish

    var doses by remember { mutableStateOf(emptyList<DoseSlot>()) }
    var adherence by remember { mutableStateOf(AdherenceSummary()) }
    LaunchedEffect(medications, todayLogs, today) {
        doses = viewModel.dosesFor(today)
        adherence = viewModel.adherenceFor(30)
    }

    val active = medications.filter { it.active }
    val inactive = medications.filterNot { it.active }
    val asNeeded = active.filter { it.asNeeded }
    val takenToday = doses.count { it.status == DoseStatus.TAKEN }
    val asNeededIds = asNeeded.map { it.id }.toSet()
    val asNeededTodayLogs = todayLogs
        .filter { it.medicationId in asNeededIds && it.date == today }
        .sortedByDescending { it.takenAt ?: 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("药物", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "记录服药与副作用，给复诊留一份依据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAddMedication) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  添加")
                }
            }
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("今天", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (doses.isEmpty()) "今天没有需要按时服用的药物"
                            else "已完成 $takenToday / ${doses.size} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                }
                if (doses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    doses.forEach { dose ->
                        DoseRow(
                            dose = dose,
                            onTake = { viewModel.logDose(dose.medication.id, today, dose.time, DoseStatus.TAKEN) },
                            onClear = { viewModel.clearDose(dose.medication.id, today, dose.time) }
                        )
                    }
                }
                if (asNeeded.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("按需使用", style = MaterialTheme.typography.titleSmall)
                    asNeeded.forEach { med ->
                        AsNeededMedicationRow(
                            medication = med,
                            logs = todayLogs.filter {
                                it.medicationId == med.id && it.date == today
                            },
                            onLog = {
                                viewModel.logDose(med.id, today, "", DoseStatus.AS_NEEDED)
                            },
                            onUndo = {
                                todayLogs
                                    .filter { it.medicationId == med.id && it.date == today }
                                    .maxByOrNull { it.takenAt ?: 0 }
                                    ?.let { viewModel.deleteMedicationLog(it.id) }
                            }
                        )
                    }
                    if (asNeededTodayLogs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("今天的按需记录", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        asNeededTodayLogs.forEach { log ->
                            val medName = asNeeded
                                .firstOrNull { it.id == log.medicationId }
                                ?.displayLabel()
                                .orEmpty()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    log.takenAt?.let { DateUtils.timeOfTimestamp(it) } ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(52.dp)
                                )
                                Text(
                                    medName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteMedicationLog(log.id) }
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "删除这条用药记录",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdherenceRing(percent = adherence.percent, hasData = adherence.total > 0)
                    Spacer(Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("近 30 天按时服药", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已服 ${adherence.taken} 次 · 漏服 ${adherence.missed} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (english) {
                                "Counts completed days and today's logged doses. Tap Take to mark it taken; tap Taken again to undo."
                            } else {
                                "统计已过去的日期，以及今天已记录的用药。点「服用」可切换为「已服」，再点一次可撤回。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                SectionTitle("在服药物", subtitle = "点击查看或修改")
            }
            items(active.size, key = { active[it].id }) { index ->
                MedicationCard(
                    medication = active[index],
                    onClick = { onEditMedication(active[index].id) }
                )
            }
        }

        if (inactive.isNotEmpty()) {
            item {
                SectionTitle("已停用", subtitle = "保留历史记录")
            }
            items(inactive.size, key = { "inactive_${inactive[it].id}" }) { index ->
                MedicationCard(
                    medication = inactive[index],
                    onClick = { onEditMedication(inactive[index].id) },
                    muted = true
                )
            }
        }

        if (medications.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有添加药物",
                    body = "添加处方药或按需药物后，可以每天打卡并设置提醒。",
                    icon = Icons.Rounded.Medication
                )
            }
            item {
                PrimaryActionButton(text = "添加第一种药物", onClick = onAddMedication)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (settings.medicationRemindersEnabled) "用药提醒已开启，可在设置中调整。"
                    else "用药提醒已关闭，可在设置中开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 按需用药的一行记录入口。按钮点击后立即写入日志，并同时给出：
 * 触觉反馈、Toast、今日次数与最近一次时间，避免看起来“没有反应”。
 */
@Composable
fun AsNeededMedicationRow(
    medication: MedicationEntity,
    logs: List<MedicationLogEntity>,
    onLog: () -> Unit,
    onUndo: () -> Unit = {},
    compact: Boolean = false
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    var feedbackTick by remember { mutableIntStateOf(0) }
    var lastCount by remember { mutableIntStateOf(logs.size) }

    // 只在数据库日志确实增加后给出成功反馈，避免写入失败时误报。
    LaunchedEffect(logs.size) {
        when {
            logs.size > lastCount -> {
                feedbackTick += 1
                Toast.makeText(
                    context,
                    translate("已记录一次", language),
                    Toast.LENGTH_SHORT
                ).show()
            }
            logs.size < lastCount -> {
                feedbackTick = 0
                Toast.makeText(
                    context,
                    translate("已撤销一次", language),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        lastCount = logs.size
    }

    LaunchedEffect(feedbackTick) {
        if (feedbackTick > 0) {
            delay(1800)
            feedbackTick = 0
        }
    }

    val latestTakenAt = logs.mapNotNull { it.takenAt }.maxOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    medication.displayLabel(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (language.isEnglish) {
                        buildString {
                            append("${logs.size} logged today")
                            latestTakenAt?.let { append(" · last ${DateUtils.timeOfTimestamp(it)}") }
                        }
                    } else {
                        buildString {
                            append("今天已记录 ${logs.size} 次")
                            latestTakenAt?.let { append(" · 最近 ${DateUtils.timeOfTimestamp(it)}") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            if (logs.isNotEmpty()) {
                IconButton(onClick = onUndo) {
                    Icon(
                        Icons.Rounded.Undo,
                        contentDescription = if (language.isEnglish) "Undo last log" else "撤销最近一次",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLog()
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("记录一次", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (feedbackTick > 0) {
            Text(
                if (language.isEnglish) "✓ Logged just now" else "✓ 刚刚记录一次",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        if (!compact) {
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun MedicationCard(
    medication: MedicationEntity,
    onClick: () -> Unit,
    muted: Boolean = false
) {
    SoftCard(
        onClick = onClick,
        containerColor = if (muted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (muted) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    doseLine(medication),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (medication.notes.isNotBlank()) {
                    Text(
                        medication.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun doseLine(medication: MedicationEntity): String {
    val dose = if (medication.dosage.isBlank()) "" else "${medication.dosage}${medication.unit}".trim()
    return if (medication.asNeeded) {
        listOf("按需", dose).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        val times = medication.times.split(',').filter { it.isNotBlank() }.joinToString("、")
        listOf(dose, times).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
