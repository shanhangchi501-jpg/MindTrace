package com.mindtrace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DiagnosisCatalog
import com.mindtrace.app.data.DiagnosisDef
import com.mindtrace.app.data.DiagnosisEntity
import com.mindtrace.app.data.displayName
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.DateField
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

@Composable
fun DiagnosisScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val diagnoses by viewModel.diagnoses.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiagnosisEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<DiagnosisEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "诊断记录",
                subtitle = "记录专业人员给出的诊断，复诊时更有条理",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { showPicker = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text("  添加")
                    }
                }
            )
        }
        item {
            InfoBanner(
                text = "这里只保存你已经得到的诊断，不会根据记录替你下诊断。ICD-10 / ICD-11 / DSM-5 为常用条目节选，找不到时可以自定义。诊断应由具备资质的专业人员作出。",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        if (diagnoses.isEmpty()) {
            item {
                SoftCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("还没有诊断记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "可以添加医生告诉你的诊断、诊断时间与备注。也可以留空，只记录自己的感受。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryActionButton(text = "添加诊断", onClick = { showPicker = true })
                    }
                }
            }
        } else {
            items(diagnoses, key = { it.id }) { diagnosis ->
                DiagnosisCard(
                    diagnosis = diagnosis,
                    english = english,
                    onEdit = { editing = diagnosis },
                    onDelete = { pendingDelete = diagnosis }
                )
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }

    if (showPicker) {
        DiagnosisPickerDialog(
            english = english,
            onDismiss = { showPicker = false },
            onSave = { entity ->
                viewModel.saveDiagnosis(entity)
                showPicker = false
            }
        )
    }

    editing?.let { diagnosis ->
        DiagnosisDetailDialog(
            initial = diagnosis,
            english = english,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveDiagnosis(it)
                editing = null
            }
        )
    }

    pendingDelete?.let { diagnosis ->
        ConfirmDialog(
            title = "删除这条诊断记录？",
            text = "只会删除这条诊断记录，不影响打卡、药物和其他记录。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteDiagnosis(diagnosis)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun DiagnosisCard(
    diagnosis: DiagnosisEntity,
    english: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SoftCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (diagnosis.code.isNotBlank()) {
                        Text(
                            diagnosis.code,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        diagnosis.displayName(english),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DiagnosisCatalog.statusLabel(diagnosis.status, english),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        " · ${DiagnosisCatalog.standardLabel(diagnosis.standard, english)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    diagnosis.diagnosedDate?.let {
                        Text(
                            " · ${DateUtils.displayDate(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (diagnosis.clinician.isNotBlank()) {
                    Text(
                        "医生 / 机构：${diagnosis.clinician}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (diagnosis.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        diagnosis.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun DiagnosisPickerDialog(
    english: Boolean,
    onDismiss: () -> Unit,
    onSave: (DiagnosisEntity) -> Unit
) {
    var standard by remember { mutableStateOf("ICD10") }
    var query by remember { mutableStateOf("") }
    var pendingDef by remember { mutableStateOf<DiagnosisDef?>(null) }
    val results = remember(standard, query) { DiagnosisCatalog.search(standard, query) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ScreenHeader(
                title = "添加诊断",
                subtitle = "选择标准，或自定义",
                onBack = onDismiss
            )
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SegmentedOptions(
                    options = listOf(
                        "ICD10" to "ICD-10",
                        "ICD11" to "ICD-11",
                        "DSM5" to "DSM-5",
                        "CUSTOM" to "自定义"
                    ),
                    selected = standard,
                    onSelect = {
                        standard = it
                        query = ""
                        pendingDef = null
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (standard == "CUSTOM") {
                    CustomDiagnosisForm(
                        english = english,
                        onSave = onSave
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索编码或名称") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "共 ${results.size} 条常用条目；找不到时可以切换到「自定义」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (standard != "CUSTOM") {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { "${it.standard}-${it.code}-${it.name}" }) { def ->
                        SoftCard(
                            containerColor = MaterialTheme.colorScheme.surface,
                            onClick = { pendingDef = def }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    def.code,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    if (english) def.nameEn else def.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (results.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的条目。切换到「自定义」可以手动填写。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    pendingDef?.let { def ->
        DiagnosisDetailDialog(
            initial = DiagnosisEntity(
                standard = def.standard,
                code = def.code,
                name = def.name,
                nameEn = def.nameEn,
                createdAt = 0
            ),
            english = english,
            lockIdentity = true,
            onDismiss = { pendingDef = null },
            onSave = {
                onSave(it)
                pendingDef = null
            }
        )
    }
}

@Composable
private fun CustomDiagnosisForm(
    english: Boolean,
    onSave: (DiagnosisEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("confirmed") }
    var date by remember { mutableStateOf<String?>(null) }
    var clinician by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("诊断名称 *") },
            placeholder = { Text("例如：医生给出的诊断名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(20) },
            label = { Text("编码（可选）") },
            placeholder = { Text("例如：F32.1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text("状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SegmentedOptions(
            options = listOf(
                "confirmed" to "已确诊",
                "suspected" to "疑似",
                "remission" to "已缓解",
                "history" to "既往诊断"
            ),
            selected = status,
            onSelect = { status = it }
        )
        Spacer(Modifier.height(10.dp))
        DateField(
            label = "确诊日期（可选）",
            isoDate = date ?: DateUtils.todayIso(),
            onDateChange = { date = it }
        )
        if (date != null) {
            TextButton(onClick = { date = null }) { Text("清除日期") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = clinician,
            onValueChange = { clinician = it.take(80) },
            label = { Text("医生 / 机构（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            label = { Text("备注（可选）") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(
            text = "保存诊断",
            enabled = name.isNotBlank(),
            onClick = {
                onSave(
                    DiagnosisEntity(
                        standard = "CUSTOM",
                        code = code.trim(),
                        name = name.trim(),
                        nameEn = "",
                        status = status,
                        diagnosedDate = date,
                        clinician = clinician.trim(),
                        note = note.trim()
                    )
                )
            }
        )
    }
}

@Composable
private fun DiagnosisDetailDialog(
    initial: DiagnosisEntity,
    english: Boolean,
    lockIdentity: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (DiagnosisEntity) -> Unit
) {
    var name by remember(initial) { mutableStateOf(if (english && initial.nameEn.isNotBlank()) initial.nameEn else initial.name) }
    var code by remember(initial) { mutableStateOf(initial.code) }
    var status by remember(initial) { mutableStateOf(initial.status) }
    var date by remember(initial) { mutableStateOf(initial.diagnosedDate) }
    var clinician by remember(initial) { mutableStateOf(initial.clinician) }
    var note by remember(initial) { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial.id == 0L) "确认诊断信息" else "编辑诊断")
        },
        text = {
            Column {
                if (lockIdentity) {
                    Text(
                        initial.code,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (english) initial.nameEn.ifBlank { initial.name } else initial.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("诊断名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(20) },
                        label = { Text("编码（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text("状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                SegmentedOptions(
                    options = listOf(
                        "confirmed" to "已确诊",
                        "suspected" to "疑似",
                        "remission" to "已缓解",
                        "history" to "既往诊断"
                    ),
                    selected = status,
                    onSelect = { status = it }
                )
                Spacer(Modifier.height(10.dp))
                DateField(
                    label = "确诊日期（可选）",
                    isoDate = date ?: DateUtils.todayIso(),
                    onDateChange = { date = it }
                )
                if (date != null) {
                    TextButton(onClick = { date = null }) { Text("清除日期") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clinician,
                    onValueChange = { clinician = it.take(80) },
                    label = { Text("医生 / 机构（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = if (lockIdentity) initial.name else name.trim(),
                            nameEn = if (lockIdentity) initial.nameEn else "",
                            code = if (lockIdentity) initial.code else code.trim(),
                            status = status,
                            diagnosedDate = date,
                            clinician = clinician.trim(),
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

