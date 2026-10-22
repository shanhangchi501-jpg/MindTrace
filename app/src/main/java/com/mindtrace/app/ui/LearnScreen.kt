package com.mindtrace.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindtrace.app.data.EducationCatalog
import com.mindtrace.app.data.EducationEntry
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

@Composable
fun LearnScreen(onOpenEntry: (String) -> Unit) {
    val context = LocalContext.current
    val english = LocalAppLanguage.current.isEnglish
    val data = remember { EducationCatalog.load(context) }

    var category by rememberSaveable { mutableStateOf("全部") }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(data, category, query) {
        val q = query.trim()
        data.entries.filter { entry ->
            (category == "全部" || entry.category == category) &&
                (q.isBlank() || listOf(
                    entry.title,
                    entry.titleEn,
                    entry.description,
                    entry.descriptionEn
                ).any { it.contains(q, ignoreCase = true) } ||
                    entry.synonyms.any { it.contains(q, ignoreCase = true) })
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("科普", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "精神类疾病、症状与药物，来自开放知识库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            InfoBanner(
                text = data.disclaimer(english),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索名称或关键词") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EducationCatalog.categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = {
                            Text(
                                EducationCatalog.categoryLabel(cat, english),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    if (english) "${filtered.size} entries" else "共 ${filtered.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (english) {
                        "Tap an entry to read the full article in the detail page. The app stays offline."
                    } else {
                        "点击条目进入二级页面阅读全文。应用保持离线，来源网址仅作文本展示。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                SoftCard {
                    Text(
                        if (english) "No matching entries. Try another keyword."
                        else "没有匹配的条目，可以换一个关键词。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { entry ->
                LearnEntryCard(
                    entry = entry,
                    english = english,
                    onClick = { onOpenEntry(entry.id) }
                )
            }
        }

        item {
            SectionTitle(
                if (english) "Sources & license" else "内容来源与许可",
                subtitle = if (english) "Open knowledge, offline" else "开放知识库，离线阅读"
            )
        }

        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                data.sources.forEach { source ->
                    Text(
                        "${source.name} · ${source.license}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (english) {
                        "Condition and symptom entries come from the Multiple Personality System Wiki. Medication entries come from Wikipedia. Both are licensed CC BY-SA 4.0 and may be shortened. Neither source is a clinical guideline. For diagnosis and treatment, consult a qualified clinician."
                    } else {
                        "疾病与症状条目来自多意识体系统 Wiki；药物条目来自维基百科。两者均采用 CC BY-SA 4.0 许可，部分内容有删节。两个来源都不是临床指南。诊断、用药和治疗方案请咨询具备资质的专业人员。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "https://creativecommons.org/licenses/by-sa/4.0/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Text(
                    if (english) "If you are in danger" else "如果你正处于危险中",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (english) {
                        "Call 120 / 110, or the mental health hotline 12356 (mainland China). If you have injured yourself or taken too much medication, go to the nearest emergency department."
                    } else {
                        "请拨打 120 / 110，或心理援助热线 12356（中国大陆）。如果已经受伤或服药过量，请立即前往最近的急诊。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun LearnEntryCard(
    entry: EducationEntry,
    english: Boolean,
    onClick: () -> Unit
) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        EducationCatalog.categoryLabel(entry.category, english),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        " · ${entry.sourceLabel()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.displayTitle(english),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.displayDescription(english),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
