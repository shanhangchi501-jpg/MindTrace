package com.mindtrace.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DoseSlot
import com.mindtrace.app.data.DoseStatus
import com.mindtrace.app.data.MedicationLogEntity
import com.mindtrace.app.data.displayLabel
import com.mindtrace.app.ui.components.DateField
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.TimeField
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text
import com.mindtrace.app.ui.i18n.translate

/**
 * 补记 / 修正历史服药数据。
 *
 * 这里不新增任何数据表或字段，也不给记录打「补记」标签：选择日期后，直接写入、
 * 撤销或删除这一天的 medication_logs 记录。依从率、趋势图与复诊报告读取的
 * 都是同一份数据，修改后会立即同步。
 */
@Composable
fun MedicationBackfillScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val medications by viewModel.allMedications.collectAsStateWithLifecycle()
    val logs by viewModel.backfillLogs.collectAsStateWithLifecycle()
    val date by viewModel.backfillDate.collectAsStateWithLifecycle()
    val today = DateUtils.todayIso()

    var doses by remember { mutableStateOf(emptyList<DoseSlot>()) }
    var asNeededTime by remember { mutableStateOf(DateUtils.nowTimeLabel()) }

    // 第一次进入默认停在昨天；之后保留用户上次选择的日期。
    LaunchedEffect(Unit) {
        if (viewModel.backfillDate.value == DateUtils.todayIso()) {
            viewModel.setBackfillDate(DateUtils.iso(DateUtils.daysAgo(1)))
        }
    }
    LaunchedEffect(date, medications, logs) {
        doses = viewModel.dosesFor(date)
    }

    val asNeeded = medications.filter { it.active && it.asNeeded }
    val medNames = medications.associate { it.id to it.displayLabel() }
    val takenCount = doses.count { it.status == DoseStatus.TAKEN }
    val sortedLogs = logs.sortedWith(
        compareBy<MedicationLogEntity> { it.time.ifBlank { "99:99" } }
            .thenBy { it.takenAt ?: 0L }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "补记服药",
            subtitle = "选择日期，直接修改那天的服药记录",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SoftCard {
                    DateField(
                        label = "日期",
                        isoDate = date,
                        onDateChange = { picked ->
                            if (picked <= today) {
                                viewModel.setBackfillDate(picked)
                            } else {
                                Toast.makeText(
                                    context,
                                    translate("不能补记未来的日期", language),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                DateUtils.parse(date)?.minusDays(1)?.let {
                                    viewModel.setBackfillDate(DateUtils.iso(it))
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "前一天")
                        }
                        Text(
                            DateUtils.displayFullDate(date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                DateUtils.parse(date)?.plusDays(1)?.let { next ->
                                    val iso = DateUtils.iso(next)
                                    if (iso <= today) viewModel.setBackfillDate(iso)
                                }
                            },
                            enabled = date < today
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "后一天")
                        }
                    }
                    Text(
                        if (doses.isEmpty()) "这一天没有需要按时服用的药物"
                        else "已服 $takenCount / ${doses.size} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SoftCard {
                    SectionTitle("按时服药", subtitle = "点一下即可在「已服 / 未服」之间切换")
                    Spacer(Modifier.height(6.dp))
                    if (doses.isEmpty()) {
                        Text(
                            "这一天没有需要按时服用的药物。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        doses.forEach { dose ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    dose.time,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(48.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dose.medication.displayLabel(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        when (dose.status) {
                                            DoseStatus.TAKEN -> "已服"
                                            DoseStatus.MISSED -> "未记录"
                                            DoseStatus.SKIPPED -> "已跳过"
                                            else -> "待服用"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val taken = dose.status != DoseStatus.TAKEN
                                        viewModel.setDoseRecord(
                                            medicationId = dose.medication.id,
                                            date = date,
                                            time = dose.time,
                                            taken = taken,
                                            takenAt = DateUtils.timestampOf(date, dose.time)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        if (dose.status == DoseStatus.TAKEN) "撤销" else "标记已服",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (asNeeded.isNotEmpty()) {
                item {
                    SoftCard {
                        SectionTitle("按需用药", subtitle = "选好时间，补写那一天的一次记录")
                        Spacer(Modifier.height(8.dp))
                        TimeField(
                            label = "服用时间",
                            time = asNeededTime,
                            onChange = { asNeededTime = it }
                        )
                        Spacer(Modifier.height(6.dp))
                        asNeeded.forEach { med ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(med.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "当天 ${logs.count { it.medicationId == med.id }} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.setDoseRecord(
                                            medicationId = med.id,
                                            date = date,
                                            time = asNeededTime,
                                            taken = true,
                                            takenAt = DateUtils.timestampOf(date, asNeededTime)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("补一次", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    SectionTitle("这一天的服药记录", subtitle = "删除某条记录后，依从率会同步变化")
                    Spacer(Modifier.height(6.dp))
                    if (sortedLogs.isEmpty()) {
                        Text(
                            "这一天还没有服药记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sortedLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val timeText = log.time.takeIf { it.isNotBlank() }
                                    ?: log.takenAt?.let { DateUtils.timeOfTimestamp(it) }
                                    ?: "—"
                                Text(
                                    timeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(52.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        medNames[log.medicationId] ?: "已删除的药物",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        when (log.status) {
                                            "taken" -> "已服"
                                            "skipped" -> "已跳过"
                                            "missed" -> "漏服"
                                            else -> log.status
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteMedicationLog(log.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "删除这条记录",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    text = "这里修改的就是原有的服药记录：写入、撤销或删除都会直接改变这一天的记录，近 30 天依从率、趋势图与复诊报告也会跟着更新。不会留下额外的「补记」标记。"
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
