package com.mindtrace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text
import com.mindtrace.app.ui.i18n.translate
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DoseSlot
import com.mindtrace.app.data.DoseStatus
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.displayLabel
import com.mindtrace.app.ui.components.AppMark
import com.mindtrace.app.ui.components.ChartPoint
import com.mindtrace.app.ui.components.MiniLineChart
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.theme.MoodColors
import com.mindtrace.app.ui.theme.MoodLabels
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onCheckIn: (String) -> Unit,
    onAddJournal: (String) -> Unit,
    onEditJournal: (Long) -> Unit,
    onOpenMedications: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenScales: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenCrisis: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val symptoms by viewModel.symptoms.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val scales by viewModel.scales.collectAsStateWithLifecycle()
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val today = DateUtils.todayIso()
    val todayCheckIn = checkIns.firstOrNull { it.date == today }
    val symptomNames = remember(symptoms) { symptoms.associate { it.id to it.name } }
    val asNeededMeds = medications.filter { it.asNeeded }
    var showAllJournals by rememberSaveable { mutableStateOf(false) }

    var doses by remember { mutableStateOf(emptyList<DoseSlot>()) }
    LaunchedEffect(medications, todayLogs, today) {
        doses = viewModel.dosesFor(today)
    }

    val lastScales = remember(scales) {
        ScaleCatalog.all.mapNotNull { def ->
            scales.firstOrNull { it.type == def.type }?.let { def to it }
        }
    }
    val recentScale = lastScales.maxByOrNull { it.second.createdAt }?.second
    val scaleDays = recentScale?.let {
        ChronoUnit.DAYS.between(
            DateUtils.toLocalDateTime(it.createdAt).toLocalDate(),
            DateUtils.today()
        )
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
                AppMark(size = 42)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${DateUtils.greeting()}${profile?.displayName?.takeIf { it.isNotBlank() }?.let { "，$it" } ?: ""}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = DateUtils.displayFullDate(today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            TodayCard(
                checkIn = todayCheckIn,
                symptomNames = symptomNames,
                onOpen = { onCheckIn(today) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.EditNote,
                    title = if (todayCheckIn == null) "记录今天" else "补充记录",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onCheckIn(today) }
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Medication,
                    title = "服药打卡",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenMedications
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Assignment,
                    title = "自评量表",
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenScales
                )
            }
        }

        if (doses.isNotEmpty() || asNeededMeds.isNotEmpty()) {
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("今天的用药", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (doses.isEmpty()) "按需用药可随时记录"
                                else "已记录 ${doses.count { it.status == DoseStatus.TAKEN }} / ${doses.size} 次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenMedications) { Text("全部") }
                    }
                    Spacer(Modifier.height(6.dp))
                    doses.take(4).forEach { dose ->
                        DoseRow(
                            dose = dose,
                            onTake = {
                                viewModel.logDose(dose.medication.id, today, dose.time, DoseStatus.TAKEN)
                            },
                            onClear = {
                                viewModel.clearDose(dose.medication.id, today, dose.time)
                            }
                        )
                    }
                    if (doses.size > 4) {
                        Text(
                            "还有 ${doses.size - 4} 次用药，去药物页查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (asNeededMeds.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("按需使用", style = MaterialTheme.typography.titleSmall)
                        asNeededMeds.forEach { med ->
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
                                },
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        item {
            SevenDayMoodCard(
                checkIns = checkIns,
                onOpenInsights = onOpenInsights
            )
        }

        item {
            SectionTitle(
                "日历与日记",
                subtitle = "补记、翻看，或写下此刻"
            )
        }

        item {
            CalendarCard(
                checkIns = checkIns,
                journal = journal,
                onEditCheckIn = onCheckIn,
                onAddJournal = onAddJournal,
                onEditJournal = onEditJournal,
                symptomNames = symptomNames
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近的日记",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onAddJournal(today) }) { Text("写一篇") }
            }
        }

        if (journal.isEmpty()) {
            item {
                Text(
                    "还没有日记。有些感受不适合填进选项里，可以在这里慢慢写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val visibleJournals = if (showAllJournals) journal else journal.take(4)
            items(visibleJournals, key = { "journal_${it.id}" }) { entry ->
                JournalCard(entry = entry, onClick = { onEditJournal(entry.id) })
            }
            if (journal.size > 4) {
                item {
                    TextButton(onClick = { showAllJournals = !showAllJournals }) {
                        Text(
                            if (showAllJournals) "收起"
                            else if (language.isEnglish) "Show all ${journal.size} entries"
                            else "查看全部 ${journal.size} 篇"
                        )
                    }
                }
            }
        }

        item {
            SoftCard(onClick = onOpenScales) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最近的自评", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                recentScale == null -> "PHQ-9 / GAD-7，约 2 分钟"
                                scaleDays != null && scaleDays <= 13 ->
                                    "${scaleLabel(recentScale)} · ${recentScale.score} 分，${bandLabel(recentScale)}"
                                else -> "距上次自评已 ${scaleDays ?: 0} 天，可以再测一次"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SoftCard(onClick = onOpenReport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("复诊报告", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "把最近的趋势、用药和量表整理成一页纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("如果此刻很难受", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "可以打开安全计划，或直接联系急救与心理援助热线。你不需要一个人扛。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            TextButton(onClick = onOpenSafety, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("安全计划")
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = onOpenCrisis, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("紧急支持")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "心迹不是医疗器械，不提供诊断或治疗建议。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun TodayCard(
    checkIn: CheckInEntity?,
    symptomNames: Map<String, String>,
    onOpen: () -> Unit
) {
    val hasContent = checkIn != null && (
        checkIn.mood != null || checkIn.anxiety != null || checkIn.sleepHours != null ||
            checkIn.symptoms.isNotBlank() || checkIn.notes.isNotBlank()
        )
    if (!hasContent) {
        SoftCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            onClick = onOpen
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("今天还没有留下记录", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "花一分钟，记下此刻的情绪、睡眠或身体感受。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val entry = checkIn ?: return
    val mood = entry.mood
    val language = LocalAppLanguage.current
    SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("今天的状态", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = mood?.let { MoodLabels[it - 1] } ?: "已记录",
                        style = MaterialTheme.typography.headlineSmall,
                        color = mood?.let { MoodColors[it - 1] } ?: MaterialTheme.colorScheme.onSurface
                    )
                    if (mood != null) {
                        Text(
                            "  $mood / 5",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            entry.sleepHours?.let {
                SmallFact("睡眠", String.format(java.util.Locale.US, "%.1f 小时", it))
            }
            entry.anxiety?.let { SmallFact("焦虑", "$it / 10") }
            entry.energy?.let { SmallFact("精力", "$it / 10") }
        }
        val symptomIds = entry.symptoms.split(',').filter { it.isNotBlank() }
        val severity = com.mindtrace.app.data.parseSymptomSeverity(entry.symptomSeverity)
        val names = symptomIds
            .mapNotNull { id ->
                symptomNames[id]?.let { name ->
                    val translated = translate(name, language)
                    severity[id]?.let { level ->
                        if (language.isEnglish) "$translated ($level/10)"
                        else "$translated（$level/10）"
                    } ?: translated
                }
            }
            .take(3)
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (language.isEnglish) {
                    "Symptoms: " + names.joinToString(", ") +
                        if (symptomIds.size > 3) ", etc." else ""
                } else {
                    "症状：" + names.joinToString("、") +
                        if (symptomIds.size > 3) " 等" else ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.notes.take(80) + if (entry.notes.length > 80) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun SmallFact(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun DoseRow(
    dose: DoseSlot,
    onTake: () -> Unit,
    onClear: () -> Unit = {},
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dose.time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                dose.medication.displayLabel(),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!compact && dose.medication.notes.isNotBlank()) {
                Text(
                    dose.medication.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        when (dose.status) {
            // 只有一个按钮：未服 -> 服用；点完变成已服，再点一下回到未服。
            DoseStatus.TAKEN -> StatusPill(
                "已服",
                MaterialTheme.colorScheme.primary,
                onClick = onClear
            )
            DoseStatus.SKIPPED -> StatusPill(
                "已跳过",
                MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onClear
            )
            DoseStatus.MISSED -> StatusPill(
                "补记",
                MaterialTheme.colorScheme.primary,
                onClick = onTake
            )
            DoseStatus.PENDING -> StatusPill(
                "服用",
                MaterialTheme.colorScheme.primary,
                onClick = onTake
            )
            DoseStatus.AS_NEEDED -> StatusPill("按需", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SevenDayMoodCard(
    checkIns: List<CheckInEntity>,
    onOpenInsights: () -> Unit
) {
    val dates = DateUtils.lastNDates(7)
    val map = checkIns.associateBy { it.date }
    val values = dates.map { date ->
        val iso = DateUtils.iso(date)
        map[iso]?.mood?.toFloat()
    }
    val recorded = values.count { it != null }
    SoftCard(onClick = onOpenInsights) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
                Text(
                    "记录了 $recorded 天 · 情绪起伏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        if (recorded == 0) {
            Text(
                "从今天开始，几天后这里会出现一条属于你的线。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MiniLineChart(values = values, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dates.forEach { date ->
                    Text(
                        DateUtils.displayShort(DateUtils.iso(date)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
private fun scaleLabel(entity: ScaleAssessmentEntity): String =
    ScaleCatalog.byType(entity.type)?.shortName ?: entity.type

@Composable
private fun bandLabel(entity: ScaleAssessmentEntity): String {
    val english = LocalAppLanguage.current.isEnglish
    return ScaleCatalog.byType(entity.type)?.let { def ->
        def.band(entity.severity)?.let { def.bandLabel(it, english) }
    } ?: entity.severity
}
