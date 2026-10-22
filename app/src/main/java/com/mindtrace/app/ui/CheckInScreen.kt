package com.mindtrace.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.SafetyCatalog
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.IntensitySlider
import com.mindtrace.app.ui.components.MoodSelector
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.theme.MoodLabels
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(
    viewModel: AppViewModel,
    date: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenCrisis: () -> Unit,
    onOpenSafety: () -> Unit
) {
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val symptoms by viewModel.symptoms.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val existing = checkIns.firstOrNull { it.date == date }
    val enabledSymptoms = remember(symptoms) { symptoms.filter { it.enabled } }
    val symptomGroups = remember(enabledSymptoms) { enabledSymptoms.groupBy { it.category } }
    val schizophreniaSpectrum = remember(profile?.conditions) {
        val keys = profile?.conditions
            ?.split(',')
            ?.map { it.trim() }
            ?: emptyList()
        keys.any { it == "schizophrenia" || it == "psychosis" }
    }
    val displayGroups = remember(symptomGroups, schizophreniaSpectrum) {
        if (schizophreniaSpectrum) {
            val priority = listOf("阳性症状", "阴性症状")
            priority.mapNotNull { key -> symptomGroups[key]?.let { key to it } } +
                symptomGroups.entries
                    .filter { it.key !in priority }
                    .map { it.key to it.value }
        } else {
            symptomGroups.entries.map { it.key to it.value }
        }
    }

    var initialized by remember(date) { mutableStateOf(false) }
    var mood by remember(date) { mutableStateOf<Int?>(null) }
    var anxiety by remember(date) { mutableStateOf<Int?>(null) }
    var irritability by remember(date) { mutableStateOf<Int?>(null) }
    var energy by remember(date) { mutableStateOf<Int?>(null) }
    var sleepHours by remember(date) { mutableStateOf<Float?>(null) }
    var sleepQuality by remember(date) { mutableStateOf<Int?>(null) }
    var appetite by remember(date) { mutableStateOf<Int?>(null) }
    var concentration by remember(date) { mutableStateOf<Int?>(null) }
    var selectedSymptoms by remember(date) { mutableStateOf<Set<String>>(emptySet()) }
    var symptomSeverity by remember(date) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedTriggers by remember(date) { mutableStateOf<Set<String>>(emptySet()) }
    var ideation by remember(date) { mutableStateOf<Int?>(null) }
    var medicationNote by remember(date) { mutableStateOf("") }
    var sideEffects by remember(date) { mutableStateOf("") }
    var notes by remember(date) { mutableStateOf("") }

    LaunchedEffect(existing) {
        if (!initialized && existing != null) {
            mood = existing.mood
            anxiety = existing.anxiety
            irritability = existing.irritability
            energy = existing.energy
            sleepHours = existing.sleepHours
            sleepQuality = existing.sleepQuality
            appetite = existing.appetite
            concentration = existing.concentration
            selectedSymptoms = existing.symptoms.split(',').filter { it.isNotBlank() }.toSet()
            symptomSeverity = com.mindtrace.app.data.parseSymptomSeverity(existing.symptomSeverity)
            selectedTriggers = existing.triggers.split(',').filter { it.isNotBlank() }.toSet()
            ideation = existing.suicidalIdeation
            medicationNote = existing.medicationNote
            sideEffects = existing.sideEffects
            notes = existing.notes
            initialized = true
        }
    }

    val hasAny = mood != null || anxiety != null || irritability != null || energy != null ||
        sleepHours != null || sleepQuality != null || appetite != null || concentration != null ||
        selectedSymptoms.isNotEmpty() || selectedTriggers.isNotEmpty() || ideation != null ||
        medicationNote.isNotBlank() || sideEffects.isNotBlank() || notes.isNotBlank()

    fun save() {
        val now = System.currentTimeMillis()
        viewModel.saveCheckIn(
            CheckInEntity(
                id = existing?.id ?: 0,
                date = date,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                mood = mood,
                anxiety = anxiety,
                irritability = irritability,
                energy = energy,
                sleepHours = sleepHours,
                sleepQuality = sleepQuality,
                appetite = appetite,
                concentration = concentration,
                symptoms = selectedSymptoms.joinToString(","),
                symptomSeverity = com.mindtrace.app.data.encodeSymptomSeverity(
                    symptomSeverity.filterKeys { it in selectedSymptoms }
                ),
                triggers = selectedTriggers.joinToString(","),
                suicidalIdeation = ideation,
                medicationNote = medicationNote.trim(),
                sideEffects = sideEffects.trim(),
                notes = notes.trim()
            )
        )
        onSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(
            title = if (date == DateUtils.todayIso()) "今天的记录" else "补记",
            subtitle = DateUtils.displayFullDate(date),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SoftCard {
                    SectionTitle("此刻的情绪", subtitle = "没有标准答案，选最接近的就好")
                    Spacer(Modifier.height(14.dp))
                    MoodSelector(value = mood, onChange = { mood = it })
                }
            }

            item {
                SoftCard {
                    SectionTitle("身体与状态", subtitle = "只有愿意记录的项目才需要动")
                    Spacer(Modifier.height(10.dp))
                    IntensitySlider("焦虑", anxiety, lowLabel = "平静", highLabel = "很焦虑") { anxiety = it }
                    Spacer(Modifier.height(8.dp))
                    IntensitySlider("易怒", irritability, lowLabel = "平稳", highLabel = "很容易") { irritability = it }
                    Spacer(Modifier.height(8.dp))
                    IntensitySlider("精力", energy, lowLabel = "枯竭", highLabel = "充沛") { energy = it }
                    Spacer(Modifier.height(8.dp))
                    IntensitySlider("食欲", appetite, lowLabel = "很差", highLabel = "很好") { appetite = it }
                    Spacer(Modifier.height(8.dp))
                    IntensitySlider("注意力", concentration, lowLabel = "涣散", highLabel = "集中") { concentration = it }
                }
            }

            item {
                SoftCard {
                    SectionTitle("睡眠")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "睡眠时长",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (sleepHours == null) "未记录"
                            else String.format(Locale.US, "%.1f 小时", sleepHours),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (sleepHours == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        if (sleepHours != null) {
                            TextButton(onClick = { sleepHours = null }) { Text("清除") }
                        }
                    }
                    Slider(
                        value = sleepHours ?: 7f,
                        onValueChange = { sleepHours = (it * 2).toInt() / 2f },
                        valueRange = 0f..24f,
                        steps = 47,
                        colors = if (sleepHours == null) {
                            androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        } else {
                            androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        }
                    )
                    Text(
                        if (sleepHours == null) "拖动滑块后才会记录睡眠时长" else "已记录，拖动可调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    IntensitySlider("睡眠质量", sleepQuality, lowLabel = "很差", highLabel = "很好") { sleepQuality = it }
                }
            }

            item {
                SoftCard {
                    SectionTitle(
                        "症状",
                        subtitle = when {
                            enabledSymptoms.isEmpty() -> "可在设置中挑选要记录的症状"
                            else -> "可多选；选中后可拖动滑块记录强度，不拖也可以"
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    if (enabledSymptoms.isEmpty()) {
                        Text(
                            "还没有启用的症状。去「设置 → 我的症状」添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        displayGroups.forEach { (category, items) ->
                            Text(
                                category,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(7.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items.forEach { symptom ->
                                    Pill(
                                        text = symptom.name,
                                        selected = symptom.id in selectedSymptoms,
                                        onClick = {
                                            if (symptom.id in selectedSymptoms) {
                                                selectedSymptoms = selectedSymptoms - symptom.id
                                                symptomSeverity = symptomSeverity - symptom.id
                                            } else {
                                                selectedSymptoms = selectedSymptoms + symptom.id
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        if (selectedSymptoms.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "症状强度（可选）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "拖动滑块记录每个症状此刻的明显程度；0 = 没有，10 = 极重。不拖也可以，点「清除」可恢复为未评分。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            enabledSymptoms
                                .filter { it.id in selectedSymptoms }
                                .forEach { symptom ->
                                    IntensitySlider(
                                        label = symptom.name,
                                        value = symptomSeverity[symptom.id],
                                        lowLabel = "轻",
                                        highLabel = "重",
                                        unsetLabel = "未评分",
                                        onClear = {
                                            symptomSeverity = symptomSeverity - symptom.id
                                        }
                                    ) { level ->
                                        symptomSeverity = symptomSeverity + (symptom.id to level)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    SectionTitle("可能的诱因", subtitle = "记录发生了什么，不必急着解释为什么")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SymptomCatalog.triggers.forEach { trigger ->
                            Pill(
                                text = trigger,
                                selected = trigger in selectedTriggers,
                                onClick = {
                                    selectedTriggers = if (trigger in selectedTriggers) {
                                        selectedTriggers - trigger
                                    } else {
                                        selectedTriggers + trigger
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            "关于伤害自己的念头",
                            modifier = Modifier.weight(1f),
                            subtitle = "如实记录有助于医生了解风险；不填也可以"
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SafetyCatalog.levels.forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ideation = if (ideation == level.value) null else level.value
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ideation == level.value,
                                onClick = { ideation = if (ideation == level.value) null else level.value }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    level.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if ((ideation ?: 0) >= 2) {
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            text = "你不需要独自处理这些念头。可以现在就联系信任的人、心理援助热线 12356，或打开安全计划。",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = onOpenCrisis, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(17.dp))
                                Text("  紧急支持")
                            }
                            Spacer(Modifier.width(14.dp))
                            TextButton(onClick = onOpenSafety, contentPadding = PaddingValues(0.dp)) {
                                Text("打开安全计划")
                            }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("用药", modifier = Modifier.weight(1f), subtitle = "记录服药与副作用，复诊时很有用")
                        TextButton(onClick = onOpenMedications) {
                            Icon(Icons.Rounded.Medication, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(" 打卡")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = medicationNote,
                        onValueChange = { medicationNote = it },
                        label = { Text("服药情况 / 调整") },
                        placeholder = { Text("例如：早上漏服了一次；今天开始减量") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sideEffects,
                        onValueChange = { sideEffects = it },
                        label = { Text("副作用") },
                        placeholder = { Text("例如：手抖、嗜睡、口干") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            item {
                SoftCard {
                    SectionTitle("还想说点什么？", subtitle = "只写给自己看")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("今天发生了什么，或是此刻的感受……") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        minLines = 5
                    )
                }
            }

            item {
                Text(
                    "记录会加密保存在本机，不会上传。你可以只保存一部分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                PrimaryActionButton(
                    text = if (existing == null) "保存今天的记录" else "更新记录",
                    enabled = hasAny,
                    onClick = { save() }
                )
                if (!hasAny) {
                    Text(
                        "至少记录一项后即可保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}
