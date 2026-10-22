package com.mindtrace.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.JournalEntryEntity
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.theme.MoodLabels
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate
import com.mindtrace.app.ui.theme.MoodColors
import java.time.LocalDate

@Composable
fun CalendarCard(
    checkIns: List<CheckInEntity>,
    journal: List<JournalEntryEntity>,
    onEditCheckIn: (String) -> Unit,
    onAddJournal: (String) -> Unit,
    onEditJournal: (Long) -> Unit,
    symptomNames: Map<String, String>
) {
    val today = DateUtils.today()
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var selectedIso by rememberSaveable { mutableStateOf(DateUtils.todayIso()) }

    val checkInMap = remember(checkIns) { checkIns.associateBy { it.date } }
    val journalByDate = remember(journal) { journal.groupBy { it.date } }
    val selectedDate = DateUtils.parse(selectedIso) ?: today
    val selectedCheckIn = checkInMap[selectedIso]
    val selectedJournal = journalByDate[selectedIso].orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (month == 1) {
                    year -= 1
                    month = 12
                } else {
                    month -= 1
                }
            }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月") }
            Text(
                DateUtils.monthLabel(year, month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = {
                if (month == 12) {
                    year += 1
                    month = 1
                } else {
                    month += 1
                }
            }) { Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月") }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DateUtils.weekdayHeaders().forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val first = LocalDate.of(year, month, 1)
        val offset = first.dayOfWeek.value - 1
        val length = first.lengthOfMonth()
        val cells: List<LocalDate?> = buildList {
            repeat(offset) { add(null) }
            for (day in 1..length) add(LocalDate.of(year, month, day))
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date == null) return@Box
                        val iso = DateUtils.iso(date)
                        val isFuture = date.isAfter(today)
                        val selected = iso == selectedIso
                        val isToday = date == today
                        val checkIn = checkInMap[iso]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .alpha(if (isFuture) 0.3f else 1f)
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable(enabled = !isFuture) { selectedIso = iso },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                checkIn?.mood?.let { mood ->
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .background(MoodColors[mood - 1], CircleShape)
                                    )
                                }
                                if (journalByDate[iso].orEmpty().isNotEmpty()) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(MoodColors[3], CircleShape))
            Text("  情绪记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
            Text("  日记", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(14.dp))

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(DateUtils.displayFullDate(selectedIso), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (selectedCheckIn == null) "这一天还没有状态记录" else "已记录状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onEditCheckIn(selectedIso) }) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(if (selectedCheckIn == null) "  补记" else "  编辑")
            }
        }
        if (selectedCheckIn != null) {
            Spacer(Modifier.height(10.dp))
            CheckInSummary(selectedCheckIn, symptomNames)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "不记得也没关系。可以从今天重新开始。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("这一天的日记", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { onAddJournal(selectedIso) }) { Text("写一篇") }
        }
        if (selectedJournal.isEmpty()) {
            Text(
                "还没有日记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            selectedJournal.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditJournal(entry.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title.ifBlank { "无标题" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    }
}

@Composable
private fun CheckInSummary(checkIn: CheckInEntity, symptomNames: Map<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        checkIn.mood?.let {
            SummaryChip(MoodLabels[it - 1], MoodColors[it - 1])
        }
        checkIn.sleepHours?.let {
            SummaryChip(String.format(java.util.Locale.US, "睡眠 %.1f 小时", it), MaterialTheme.colorScheme.tertiary)
        }
        checkIn.anxiety?.let {
            SummaryChip("焦虑 $it/10", MaterialTheme.colorScheme.secondary)
        }
    }
    val language = LocalAppLanguage.current
    val severity = com.mindtrace.app.data.parseSymptomSeverity(checkIn.symptomSeverity)
    val names = checkIn.symptoms.split(',').filter { it.isNotBlank() }
        .mapNotNull { id ->
            symptomNames[id]?.let { name ->
                val translated = translate(name, language)
                severity[id]?.let { level ->
                    if (language.isEnglish) "$translated ($level/10)"
                    else "$translated（$level/10）"
                } ?: translated
            }
        }
    if (names.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (language.isEnglish) "Symptoms: " + names.joinToString(", ")
            else "症状：" + names.joinToString("、"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (checkIn.notes.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            checkIn.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun JournalCard(entry: JournalEntryEntity, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "无标题" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    DateUtils.displayDate(entry.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.mood?.let {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(MoodColors[it - 1], CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.tags.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                entry.tags.split(',').filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
