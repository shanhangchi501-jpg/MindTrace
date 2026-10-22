package com.mindtrace.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.SymptomEntity
import com.mindtrace.app.ui.components.CheckRow
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard

@Composable
fun SymptomSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val symptoms by viewModel.symptoms.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SymptomEntity?>(null) }

    val filtered = remember(symptoms, query) {
        if (query.isBlank()) symptoms
        else symptoms.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val groups = remember(filtered) { filtered.groupBy { it.category } }
    val enabledCount = symptoms.count { it.enabled }

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
                title = "我的症状",
                subtitle = "已启用 $enabledCount 项 · 只会在打卡时出现",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text("  自定义")
                    }
                }
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索症状") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            InfoBanner(
                text = "不必把所有症状都打开。选择你最想在复诊时讲清楚的几项，打卡会更轻松。"
            )
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    "没有找到相关症状。可以添加一个自定义症状。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        groups.forEach { (category, items) ->
            item {
                SectionTitle(category)
            }
            items(items, key = { it.id }) { symptom ->
                SoftCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckRow(
                            text = symptom.name + if (symptom.isCustom) "（自定义）" else "",
                            checked = symptom.enabled,
                            modifier = Modifier.weight(1f),
                            onToggle = { viewModel.setSymptomEnabled(symptom.id, it) }
                        )
                        if (symptom.isCustom) {
                            IconButton(onClick = { pendingDelete = symptom }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除自定义症状",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加自定义症状") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("名称") },
                        placeholder = { Text("例如：解离感、手抖") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "自定义症状会出现在打卡里，也可以随时删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.addCustomSymptom(name)
                        showAdd = false
                    }
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }

    pendingDelete?.let { symptom ->
        ConfirmDialog(
            title = "删除这个自定义症状？",
            text = "已保存记录中的这个症状名称将无法显示，但不会删除那天的其他记录。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteCustomSymptom(symptom.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
