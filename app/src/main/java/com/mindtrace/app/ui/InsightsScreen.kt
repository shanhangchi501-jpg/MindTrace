package com.mindtrace.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.AdherenceSummary
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.averageAnxiety
import com.mindtrace.app.data.averageMood
import com.mindtrace.app.data.averageSleep
import com.mindtrace.app.data.parseSymptomSeverity
import com.mindtrace.app.data.symptomFrequency
import com.mindtrace.app.ui.components.AdherenceRing
import com.mindtrace.app.ui.components.ChartPoint
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.components.FrequencyBars
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.LineTrendChart
import com.mindtrace.app.ui.components.MetricTile
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import java.util.Locale

@Composable
fun InsightsScreen(
    viewModel: AppViewModel,
    onOpenScales: () -> Unit,
    onOpenReport: () -> Unit
) {
    val days by viewModel.insightsDays.collectAsStateWithLifecycle()
    val checkIns by viewModel.insightsCheckIns.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    val symptoms by viewModel.symptoms.collectAsStateWithLifecycle()
    val scales by viewModel.scales.collectAsStateWithLifecycle()
    var adherence by remember { mutableStateOf(AdherenceSummary()) }
    LaunchedEffect(days) { adherence = viewModel.adherenceFor(days) }

    val dates = remember(days) { DateUtils.lastNDates(days) }
    val byDate = remember(checkIns) { checkIns.associateBy { it.date } }
    val moodPoints = dates.map { date ->
        val iso = DateUtils.iso(date)
        ChartPoint(DateUtils.displayShort(iso), byDate[iso]?.mood?.toFloat())
    }
    val anxietyPoints = dates.map { date ->
        val iso = DateUtils.iso(date)
        ChartPoint(DateUtils.displayShort(iso), byDate[iso]?.anxiety?.toFloat())
    }
    val sleepPoints = dates.map { date ->
        val iso = DateUtils.iso(date)
        ChartPoint(DateUtils.displayShort(iso), byDate[iso]?.sleepHours)
    }
    val symptomNames = remember(symptoms) { symptoms.associate { it.id to it.name } }
    val topSymptoms = remember(checkIns, symptomNames) {
        checkIns.symptomFrequency()
            .map { (id, count) -> (symptomNames[id] ?: id) to count }
            .take(6)
    }
    val severityByDate = remember(checkIns) {
        checkIns.associate { it.date to parseSymptomSeverity(it.symptomSeverity) }
    }
    val severitySymptoms = remember(checkIns, symptomNames) {
        val counts = mutableMapOf<String, Int>()
        checkIns.forEach { entry ->
            parseSymptomSeverity(entry.symptomSeverity).keys.forEach { id ->
                counts[id] = (counts[id] ?: 0) + 1
            }
        }
        counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, _) -> symptomNames[id]?.let { id to it } }
    }
    var severitySymptomId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(severitySymptoms) {
        if (severitySymptomId == null || severitySymptoms.none { it.first == severitySymptomId }) {
            severitySymptomId = severitySymptoms.firstOrNull()?.first
        }
    }
    val currentSeveritySymptom = severitySymptomId ?: severitySymptoms.firstOrNull()?.first
    val severityPoints = dates.map { date ->
        val iso = DateUtils.iso(date)
        val entry = byDate[iso]
        val level = severityByDate[iso]?.get(currentSeveritySymptom)
        val value = when {
            entry == null -> null
            level != null -> level.toFloat()
            currentSeveritySymptom != null &&
                entry.symptoms.split(',').contains(currentSeveritySymptom) -> null
            currentSeveritySymptom != null -> 0f
            else -> null
        }
        ChartPoint(DateUtils.displayShort(iso), value)
    }
    val severityAverage = severityPoints.mapNotNull { it.value }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
    val recordedDays = checkIns.count { entry ->
        entry.mood != null || entry.anxiety != null || entry.sleepHours != null || entry.symptoms.isNotBlank()
    }
    val avgMood = checkIns.averageMood()
    val avgAnxiety = checkIns.averageAnxiety()
    val avgSleep = checkIns.averageSleep()
    val observation = sleepMoodObservation(checkIns)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("趋势", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "看见变化，而不是审判自己。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SegmentedOptions(
                options = listOf(7 to "7 天", 14 to "14 天", 30 to "30 天", 90 to "90 天"),
                selected = days,
                onSelect = { viewModel.setInsightsDays(it) }
            )
        }
        item {
            SoftCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "记录天数",
                        value = "$recordedDays",
                        suffix = " / $days 天",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "平均情绪",
                        value = avgMood?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        suffix = if (avgMood != null) " / 5" else "",
                        modifier = Modifier.weight(1f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    MetricTile(
                        label = "平均睡眠",
                        value = avgSleep?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        suffix = if (avgSleep != null) (if (english) " h" else " 小时") else "",
                        modifier = Modifier.weight(1f),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (avgAnxiety != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "平均焦虑 ${String.format(Locale.US, "%.1f", avgAnxiety)} / 10 · 记录是连续的，空白也很正常。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (recordedDays == 0) {
            item {
                EmptyState(
                    title = "还没有足够的记录",
                    body = "连续记录几天后，这里会出现情绪、睡眠和症状的变化。",
                    icon = Icons.Rounded.Insights
                )
            }
        } else {
            item {
                SoftCard {
                    SectionTitle("情绪", subtitle = "1 = 很低落，5 = 很好")
                    Spacer(Modifier.height(10.dp))
                    LineTrendChart(
                        points = moodPoints,
                        color = MaterialTheme.colorScheme.primary,
                        minValue = 1f,
                        maxValue = 5f
                    )
                }
            }
            item {
                SoftCard {
                    SectionTitle("焦虑", subtitle = "0 = 平静，10 = 很焦虑")
                    Spacer(Modifier.height(10.dp))
                    LineTrendChart(
                        points = anxietyPoints,
                        color = MaterialTheme.colorScheme.secondary,
                        minValue = 0f,
                        maxValue = 10f
                    )
                }
            }
            item {
                SoftCard {
                    SectionTitle("睡眠", subtitle = "小时")
                    Spacer(Modifier.height(10.dp))
                    LineTrendChart(
                        points = sleepPoints,
                        color = MaterialTheme.colorScheme.tertiary,
                        minValue = 0f,
                        maxValue = 12f
                    )
                }
            }
            if (severitySymptoms.isNotEmpty()) {
                item {
                    SoftCard {
                        SectionTitle(
                            "症状强度",
                            subtitle = if (english) {
                                "0 = none, 10 = extreme; blank = no record that day"
                            } else {
                                "0 = 没有，10 = 极重；空白表示当天没有记录"
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            severitySymptoms.forEach { (id, name) ->
                                Pill(
                                    text = name,
                                    selected = id == currentSeveritySymptom,
                                    onClick = { severitySymptomId = id }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (severityAverage != null) {
                            Text(
                                if (english) {
                                    "Average ${String.format(Locale.US, "%.1f", severityAverage)} / 10"
                                } else {
                                    "平均 ${String.format(Locale.US, "%.1f", severityAverage)} / 10"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        LineTrendChart(
                            points = severityPoints,
                            color = MaterialTheme.colorScheme.secondary,
                            minValue = 0f,
                            maxValue = 10f
                        )
                    }
                }
            }
            if (observation != null) {
                item {
                    SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("一条观察", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            observation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "这只是记录中的统计关联，不代表因果。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            item {
                SoftCard {
                    SectionTitle("常见症状", subtitle = "按出现天数排序")
                    Spacer(Modifier.height(12.dp))
                    FrequencyBars(
                        items = topSymptoms,
                        totalDays = recordedDays,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdherenceRing(
                        percent = adherence.percent,
                        color = MaterialTheme.colorScheme.tertiary,
                        hasData = adherence.total > 0
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("用药依从", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已服 ${adherence.taken} · 漏服 ${adherence.missed}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "统计过去 $days 天内已结束的日期与今天已记录的用药",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        item {
            ScaleSummaryCard(scales = scales, onClick = onOpenScales)
        }

        item {
            SoftCard(onClick = onOpenReport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("把这段时间带去复诊", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "生成包含趋势、用药与量表的 PDF 报告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            InfoBanner(
                text = "趋势页只做记录汇总，不提供诊断。任何治疗调整，请和医生讨论。",
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScaleSummaryCard(scales: List<ScaleAssessmentEntity>, onClick: () -> Unit) {
    val english = LocalAppLanguage.current.isEnglish
    val latest = ScaleCatalog.all.mapNotNull { def ->
        scales.firstOrNull { it.type == def.type }?.let { def to it }
    }.sortedByDescending { it.second.createdAt }.take(3)
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自评量表", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "Common screens · take them as needed" else "常用自评 · 按需完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        }
        if (latest.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "还没有测评记录，约 2 分钟完成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                latest.forEach { (def, result) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp)
                    ) {
                        Text(def.shortName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${result.score} / ${def.maxScore}",
                            style = MaterialTheme.typography.titleLarge,
                            color = scoreColor(result, def.type)
                        )
                        Text(
                            def.band(result.severity)?.let { def.bandLabel(it, english) } ?: result.severity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun scoreColor(result: ScaleAssessmentEntity, type: String): Color {
    val score = result.score
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    return when {
        score <= 4 -> if (dark) colors.primary else Color(0xFF6B8F7B)
        score <= 9 -> if (dark) colors.tertiary else Color(0xFF8A9A6B)
        score <= 14 -> if (dark) colors.secondary else Color(0xFFB8835E)
        else -> if (dark) colors.error else Color(0xFFA9443E)
    }
}

private fun sleepMoodObservation(checkIns: List<CheckInEntity>): String? {
    val good = checkIns.filter { (it.sleepHours ?: 0f) >= 7f && it.mood != null }
    val poor = checkIns.filter { (it.sleepHours ?: 7f) < 7f && it.mood != null }
    if (good.size < 2 || poor.size < 2) return null
    val goodAvg = good.mapNotNull { it.mood }.average()
    val poorAvg = poor.mapNotNull { it.mood }.average()
    val diff = goodAvg - poorAvg
    return when {
        diff >= 0.8 -> "睡眠达到 7 小时的日子，平均情绪比不足 7 小时时高 ${String.format(Locale.US, "%.1f", diff)} 分。"
        diff <= -0.8 -> "睡眠不足 7 小时的日子，平均情绪反而更高。记录中的关联可能受很多因素影响。"
        else -> "睡眠时长与情绪暂时没有明显的统计差异。"
    }
}
