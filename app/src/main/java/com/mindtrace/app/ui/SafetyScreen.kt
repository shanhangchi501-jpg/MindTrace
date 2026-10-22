package com.mindtrace.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.CrisisContactEntity
import com.mindtrace.app.data.SafetyCatalog
import com.mindtrace.app.data.SafetyPlanEntity
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.WarmBackground
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate

@Composable
fun SafetyScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCrisis: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val plan by viewModel.safetyPlan.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var initialized by remember { mutableStateOf(false) }
    var warningSigns by remember { mutableStateOf("") }
    var copingStrategies by remember { mutableStateOf("") }
    var reasonsToLive by remember { mutableStateOf("") }
    var environmentSteps by remember { mutableStateOf("") }
    var professionalNote by remember { mutableStateOf("") }

    var showContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<CrisisContactEntity?>(null) }
    var pendingDeleteContact by remember { mutableStateOf<CrisisContactEntity?>(null) }

    LaunchedEffect(plan) {
        if (!initialized && plan != null) {
            warningSigns = plan!!.warningSigns
            copingStrategies = plan!!.copingStrategies
            reasonsToLive = plan!!.reasonsToLive
            environmentSteps = plan!!.environmentSteps
            professionalNote = plan!!.professionalNote
            initialized = true
        }
    }

    fun dial(number: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.replace(" ", "")}")))
        }
    }

    fun savePlan() {
        viewModel.saveSafetyPlan(
            SafetyPlanEntity(
                warningSigns = warningSigns.trim(),
                copingStrategies = copingStrategies.trim(),
                reasonsToLive = reasonsToLive.trim(),
                environmentSteps = environmentSteps.trim(),
                professionalNote = professionalNote.trim()
            )
        )
        Toast.makeText(
            context,
            translate("安全计划已保存在本机", language),
            Toast.LENGTH_SHORT
        ).show()
    }

    WarmBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    title = "安全计划",
                    subtitle = "在状态还好时，先为难受的时刻留一封信",
                    onBack = onBack,
                    actions = {
                        TextButton(onClick = onCrisis) { Text("紧急支持") }
                    }
                )
            }
            item {
                SoftCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("如果你正处于危险中", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "请不要等待。立即拨打 120 或 110；也可以拨打全国心理援助热线 12356（中国大陆）。如你在其他地区，请拨打当地急救电话。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onCrisis, contentPadding = PaddingValues(0.dp)) {
                                Text("打开紧急支持")
                            }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    SectionTitle("提前写下来", subtitle = "这些内容只保存在本机")
                    Spacer(Modifier.height(12.dp))
                    PlanField(
                        label = "哪些迹象说明我正在变得更糟？",
                        value = warningSigns,
                        onChange = { warningSigns = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    PlanField(
                        label = "过去哪些小事曾帮我撑过一阵？",
                        value = copingStrategies,
                        onChange = { copingStrategies = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    PlanField(
                        label = "让我愿意再等等的理由",
                        value = reasonsToLive,
                        onChange = { reasonsToLive = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    PlanField(
                        label = "需要暂时远离的地方或物品",
                        value = environmentSteps,
                        onChange = { environmentSteps = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    PlanField(
                        label = "我的医生 / 咨询师与就诊信息",
                        value = professionalNote,
                        onChange = { professionalNote = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryActionButton(text = "保存安全计划", onClick = { savePlan() })
                }
            }

            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            "可以联系的人",
                            modifier = Modifier.weight(1f),
                            subtitle = "添加信任的人或专业人员"
                        )
                        TextButton(onClick = {
                            editingContact = null
                            showContactDialog = true
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("  添加")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (contacts.isEmpty()) {
                        Text(
                            "还没有联系人。可以添加家人、朋友或你的医生。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        contacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingContact = contact; showContactDialog = true }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${contact.name}${if (contact.relation.isNotBlank()) " · ${contact.relation}" else ""}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${contactKindLabel(contact.kind)} · ${contact.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { dial(contact.phone) }) {
                                    Icon(Icons.Rounded.Phone, contentDescription = "拨打", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { pendingDeleteContact = contact }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    SectionTitle("随时可以拨打", subtitle = "内置的公共支持资源")
                    Spacer(Modifier.height(10.dp))
                    SafetyCatalog.crisisResources.forEach { resource ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(resource.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    resource.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { dial(resource.number) }) {
                                Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" ${resource.number}")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "以上号码适用于中国大陆。如果你不在中国大陆，请拨打当地急救电话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("5-4-3-2-1 接地练习", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "当脑子很吵时，把注意力带回此刻。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SafetyCatalog.groundingSteps.forEachIndexed { index, step ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    text = "安全计划不是合同，也不是必须完成的任务。它只是提前写好的提醒：在最难的时候，你还可以做哪些小事。",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showContactDialog) {
        ContactDialog(
            existing = editingContact,
            onDismiss = { showContactDialog = false },
            onSave = { contact ->
                viewModel.saveContact(contact)
                showContactDialog = false
            }
        )
    }

    pendingDeleteContact?.let { contact ->
        com.mindtrace.app.ui.components.ConfirmDialog(
            title = "删除这个联系人？",
            text = "只会删除联系方式，不影响安全计划的其他内容。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteContact(contact)
                pendingDeleteContact = null
            },
            onDismiss = { pendingDeleteContact = null }
        )
    }
}

@Composable
private fun PlanField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

@Composable
private fun ContactDialog(
    existing: CrisisContactEntity?,
    onDismiss: () -> Unit,
    onSave: (CrisisContactEntity) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var relation by remember(existing) { mutableStateOf(existing?.relation ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: "trusted") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加联系人" else "编辑联系人") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("称呼") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("关系（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("电话号码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "trusted" to "信任的人",
                        "professional" to "专业人员",
                        "emergency" to "紧急联系人"
                    ).forEach { (value, label) ->
                        com.mindtrace.app.ui.components.Pill(
                            text = label,
                            selected = kind == value,
                            onClick = { kind = value }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    onSave(
                        CrisisContactEntity(
                            id = existing?.id ?: 0,
                            name = name.trim(),
                            relation = relation.trim(),
                            phone = phone.trim(),
                            kind = kind,
                            sortOrder = existing?.sortOrder ?: 0
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

fun contactKindLabel(kind: String): String = when (kind) {
    "professional" -> "专业人员"
    "emergency" -> "紧急联系人"
    else -> "信任的人"
}
