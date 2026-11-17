package com.mindtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.i18n.LocalAppLanguage

@Composable
fun JournalHistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenJournal: (Long, String) -> Unit
) {
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    val sorted = remember(journal) {
        journal.sortedWith(compareByDescending<com.mindtrace.app.data.JournalEntryEntity> { it.date }.thenByDescending { it.createdAt })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "全部日记",
            subtitle = if (english) "${sorted.size} entries" else "共 ${sorted.size} 篇",
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
                        title = "还没有日记",
                        body = "写下的第一篇日记会出现在这里。",
                        icon = Icons.Rounded.MenuBook
                    )
                }
            } else {
                items(sorted.size, key = { sorted[it].id }) { index ->
                    val entry = sorted[index]
                    JournalCard(entry = entry, onClick = { onOpenJournal(entry.id, entry.date) })
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
