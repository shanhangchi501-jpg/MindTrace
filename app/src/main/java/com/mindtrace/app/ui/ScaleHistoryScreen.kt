package com.mindtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

@Composable
fun ScaleHistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val scales by viewModel.scales.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    var detail by remember { mutableStateOf<ScaleAssessmentEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ScaleAssessmentEntity?>(null) }
    val sorted = remember(scales) { scales.sortedByDescending { it.createdAt } }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "全部测评",
            subtitle = if (english) "All ${sorted.size} assessments" else "共 ${sorted.size} 条记录",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sorted.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有测评记录",
                        body = "完成一次自评量表后，记录会出现在这里。",
                        icon = Icons.Rounded.Assignment
                    )
                }
            } else {
                items(sorted.size, key = { sorted[it].id }) { index ->
                    val item = sorted[index]
                    val def = ScaleCatalog.byType(item.type)
                    val band = def?.band(item.severity)
                    SoftCard(onClick = { detail = item }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    def?.let { if (english) it.nameEn else it.shortName } ?: item.type,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${DateUtils.displayTimestamp(item.createdAt)} · ${def?.formatScore(item.score) ?: item.score}${def?.let { " / ${it.formatMaxScore()}" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (band != null) {
                                    Text(
                                        def!!.bandLabel(band, english),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDelete = item }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除这条记录",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    detail?.let { item ->
        val def = ScaleCatalog.byType(item.type)
        val band = def?.band(item.severity)
        AlertDialog(
            onDismissRequest = { detail = null },
            title = {
                Text(
                    def?.let { if (english) it.shortName else it.name } ?: item.type,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        "${DateUtils.displayTimestamp(item.createdAt)} · ${def?.formatScore(item.score) ?: item.score}${def?.let { " / ${it.formatMaxScore()}" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    if (band != null) {
                        Text(
                            def!!.bandLabel(band, english),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(def.bandAdvice(band, english), style = MaterialTheme.typography.bodySmall)
                    }
                    val note = def?.noteFor(english).orEmpty()
                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val source = def?.sourceFor(english).orEmpty()
                    if (source.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text(if (english) "Close" else "关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val current = detail
                    detail = null
                    if (current != null) pendingDelete = current
                }) { Text("删除") }
            }
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "删除这条测评记录？",
            text = "只会删除这一次的结果，不影响其他记录。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteScale(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
