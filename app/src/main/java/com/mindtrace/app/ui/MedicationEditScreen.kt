package com.mindtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.DateField
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.TimeField

private val units = listOf("mg", "g", "ml", "片", "粒", "滴", "喷", "其他")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationEditScreen(
    viewModel: AppViewModel,
    medicationId: Long,
    onBack: () -> Unit
) {
    val medications by viewModel.allMedications.collectAsStateWithLifecycle()
    val existing = remember(medications, medicationId) {
        medications.firstOrNull { it.id == medicationId }
    }
    var initialized by remember(medicationId) { mutableStateOf(false) }

    var name by remember(medicationId) { mutableStateOf("") }
    var dosage by remember(medicationId) { mutableStateOf("") }
    var unit by remember(medicationId) { mutableStateOf("mg") }
    var times by remember(medicationId) { mutableStateOf(listOf("08:00")) }
    var asNeeded by remember(medicationId) { mutableStateOf(false) }
    var startDate by remember(medicationId) { mutableStateOf(DateUtils.todayIso()) }
    var hasEndDate by remember(medicationId) { mutableStateOf(false) }
    var endDate by remember(medicationId) { mutableStateOf(DateUtils.todayIso()) }
    var active by remember(medicationId) { mutableStateOf(true) }
    var notes by remember(medicationId) { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!initialized && existing != null && medicationId > 0) {
            name = existing.name
            dosage = existing.dosage
            unit = existing.unit
            times = existing.times.split(',').filter { it.isNotBlank() }.ifEmpty { listOf("08:00") }
            asNeeded = existing.asNeeded
            startDate = existing.startDate
            hasEndDate = existing.endDate != null
            endDate = existing.endDate ?: DateUtils.todayIso()
            active = existing.active
            notes = existing.notes
            initialized = true
        }
    }

    val canSave = name.isNotBlank() &&
        (asNeeded || times.isNotEmpty()) &&
        (!hasEndDate || endDate >= startDate)

    fun save() {
        val now = System.currentTimeMillis()
        viewModel.saveMedication(
            MedicationEntity(
                id = existing?.id ?: 0,
                name = name.trim(),
                dosage = dosage.trim(),
                unit = unit,
                times = if (asNeeded) "" else times.distinct().sorted().joinToString(","),
                asNeeded = asNeeded,
                startDate = startDate,
                endDate = if (hasEndDate) endDate else null,
                active = active,
                notes = notes.trim(),
                createdAt = existing?.createdAt ?: now
            )
        )
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(
            title = if (medicationId == 0L) "添加药物" else "编辑药物",
            subtitle = "按医嘱填写；不确定的剂量可以留空",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SoftCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("药物名称 *") },
                        placeholder = { Text("例如：舍曲林") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dosage,
                            onValueChange = { dosage = it },
                            label = { Text("剂量") },
                            placeholder = { Text("50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1.4f)) {
                            Text("单位", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(5.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                units.forEach { option ->
                                    Pill(text = option, selected = unit == option, onClick = { unit = option })
                                }
                            }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("按需服用", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "不设固定时间，需要时记录一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = asNeeded, onCheckedChange = { asNeeded = it })
                    }
                    if (!asNeeded) {
                        Spacer(Modifier.height(14.dp))
                        SectionTitle("每天的时间")
                        Spacer(Modifier.height(8.dp))
                        times.forEachIndexed { index, time ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TimeField(
                                    label = "第 ${index + 1} 次",
                                    time = time,
                                    modifier = Modifier.weight(1f),
                                    onChange = { newTime ->
                                        times = times.toMutableList().also { it[index] = newTime }
                                    }
                                )
                                if (times.size > 1) {
                                    IconButton(onClick = {
                                        times = times.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "删除这个时间")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(
                            onClick = {
                                if (times.size < 6) times = (times + "20:00").distinct()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("  添加一个时间")
                        }
                    }
                }
            }

            item {
                SoftCard {
                    DateField(label = "开始日期", isoDate = startDate, onDateChange = { startDate = it })
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("设置结束日期", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "停药后也可以保留历史记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = hasEndDate, onCheckedChange = { hasEndDate = it })
                    }
                    if (hasEndDate) {
                        Spacer(Modifier.height(10.dp))
                        DateField(label = "结束日期", isoDate = endDate, onDateChange = { endDate = it })
                        if (endDate < startDate) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "结束日期不能早于开始日期",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仍在服用", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "关闭后不再出现在今日用药与提醒里",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }

            item {
                SoftCard {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("备注") },
                        placeholder = { Text("例如：饭后服用；医生要求两周后复查") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }

            item {
                InfoBanner(
                    text = "提醒只会显示「到时间了，记得照顾自己」这类脱敏文案，除非你在设置中关闭脱敏。剂量调整请先咨询医生。"
                )
            }

            if (medicationId > 0 && existing != null) {
                item {
                    TextButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text("  删除这条药物记录")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                PrimaryActionButton(text = "保存", enabled = canSave, onClick = { save() })
                if (!canSave) {
                    Text(
                        "请填写药物名称；若为定时服用，至少保留一个时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }

    if (showDelete && existing != null) {
        ConfirmDialog(
            title = "删除这条药物记录？",
            text = "对应的服药记录也会一起删除，无法恢复。若只是停药，建议关闭「仍在服用」而不是删除。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.deleteMedication(existing)
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }
}
