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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.JournalEntryEntity
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.DateField
import com.mindtrace.app.ui.components.MoodSelector
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import kotlinx.coroutines.launch

private val presetTags = listOf("复诊", "药物", "睡眠", "家庭", "工作", "关系", "闪回", "一件小事", "其他")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEditScreen(
    viewModel: AppViewModel,
    journalId: Long,
    initialDate: String,
    onBack: () -> Unit
) {
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val existing = remember(journal, journalId) { journal.firstOrNull { it.id == journalId } }
    val scope = rememberCoroutineScope()

    var initialized by remember(journalId) { mutableStateOf(false) }
    var date by rememberSaveable(journalId, initialDate) { mutableStateOf(initialDate) }
    var title by remember(journalId) { mutableStateOf("") }
    var content by remember(journalId) { mutableStateOf("") }
    var mood by remember(journalId) { mutableStateOf<Int?>(null) }
    var tags by remember(journalId) { mutableStateOf<Set<String>>(emptySet()) }
    var customTags by remember(journalId) { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!initialized && existing != null && journalId > 0) {
            date = existing.date
            title = existing.title
            content = existing.content
            mood = existing.mood
            tags = existing.tags.split(',').filter { it.isNotBlank() }.toSet()
            initialized = true
        }
    }

    fun allTags(): List<String> = (tags + customTags.split(',', '，')
        .map(String::trim).filter(String::isNotBlank)).distinct()

    fun save() {
        val now = System.currentTimeMillis()
        scope.launch {
            viewModel.saveJournal(
                JournalEntryEntity(
                    id = existing?.id ?: 0,
                    date = date,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    title = title.trim(),
                    content = content.trim(),
                    mood = mood,
                    tags = allTags().joinToString(",")
                )
            )
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(
            title = if (journalId == 0L) "写日记" else "编辑日记",
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
                    DateField(label = "日期", isoDate = date, onDateChange = { date = it })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题（可选）") },
                        placeholder = { Text("给今天起个名字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                SoftCard {
                    SectionTitle("此刻的情绪", subtitle = "可跳过")
                    Spacer(Modifier.height(12.dp))
                    MoodSelector(value = mood, onChange = { mood = it })
                }
            }
            item {
                SoftCard {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("写给自己的话") },
                        placeholder = { Text("不用讲道理，想到哪里写到哪里……") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        minLines = 8
                    )
                }
            }
            item {
                SoftCard {
                    SectionTitle("标签", subtitle = "方便以后翻找")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetTags.forEach { tag ->
                            Pill(
                                text = tag,
                                selected = tag in tags,
                                onClick = {
                                    tags = if (tag in tags) tags - tag else tags + tag
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customTags,
                        onValueChange = { customTags = it },
                        label = { Text("其他标签（用逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (journalId > 0 && existing != null) {
                item {
                    TextButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text("  删除这篇日记")
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
                PrimaryActionButton(
                    text = "保存",
                    enabled = title.isNotBlank() || content.isNotBlank(),
                    onClick = { save() }
                )
            }
        }
    }

    if (showDelete && existing != null) {
        ConfirmDialog(
            title = "删除这篇日记？",
            text = "删除后无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.deleteJournal(existing)
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }
}
