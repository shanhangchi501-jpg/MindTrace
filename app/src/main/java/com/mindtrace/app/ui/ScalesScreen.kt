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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
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
import com.mindtrace.app.data.ScaleDef
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

@Composable
fun ScalesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onStartScale: (String) -> Unit,
    onOpenAllScales: () -> Unit
) {
    val scales by viewModel.scales.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    var pendingDelete by remember { mutableStateOf<ScaleAssessmentEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "自评量表",
                subtitle = if (english) "For tracking, not diagnosis" else "用于追踪，不是诊断",
                onBack = onBack,
                actions = {
                    TextButton(onClick = onOpenAllScales) {
                        Text("更多量表")
                    }
                }
            )
        }
        item {
            InfoBanner(
                text = "量表只用于筛查和追踪，分数会随状态波动，不能替代医生的诊断。若某题让你感到不适，可以随时退出。",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        item {
            SectionTitle(
                "常用量表",
                subtitle = if (english) {
                    "Frequently used screens"
                } else {
                    "常用筛查"
                }
            )
        }
        items(ScaleCatalog.featured, key = { it.type }) { def ->
            ScaleCard(
                def = def,
                latest = scales.firstOrNull { it.type == def.type },
                english = english,
                onStart = { onStartScale(def.type) }
            )
        }
        item {
            SoftCard(onClick = onOpenAllScales) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("更多量表", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (english) {
                                "Browse all ${ScaleCatalog.all.size} scales by category"
                            } else {
                                "按类别浏览全部 ${ScaleCatalog.all.size} 个量表"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (scales.isNotEmpty()) {
            item {
                SectionTitle("最近测评", subtitle = "可以删除不需要的记录")
            }
            items(scales.take(8), key = { "history-${it.id}" }) { item ->
                RecentScaleRow(
                    item = item,
                    english = english,
                    onDelete = { pendingDelete = item }
                )
            }
        }
        item {
            Text(
                if (english) {
                    "Scale sources and copyright belong to their original authors. " +
                        "Scores are for screening and clinical communication only; they are not diagnoses. " +
                        "Some scales require permission before electronic redistribution."
                } else {
                    "量表来源与版权归原作者或出版方所有。分数仅用于筛查和就诊沟通，不是诊断。" +
                        "部分量表的电子版再分发需要获得授权，公开发布前请逐项确认。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
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

@Composable
fun ScalesAllScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onStartScale: (String) -> Unit
) {
    val scales by viewModel.scales.collectAsStateWithLifecycle()
    val english = LocalAppLanguage.current.isEnglish
    val categories = remember(english) { ScaleCatalog.categories(english) }

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
                title = "全部量表",
                subtitle = "按类别浏览；量表不是诊断",
                onBack = onBack
            )
        }
        item {
            InfoBanner(
                text = "题数较多的量表可以分几次完成，也可以只做最相关的几个。任何时候感到不适都可以退出。",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        categories.forEach { category ->
            item {
                SectionTitle(category)
            }
            items(
                ScaleCatalog.inCategory(category, english),
                key = { "all-${it.type}" }
            ) { def ->
                ScaleCard(
                    def = def,
                    latest = scales.firstOrNull { it.type == def.type },
                    english = english,
                    compact = true,
                    onStart = { onStartScale(def.type) }
                )
            }
        }
        item {
            Text(
                if (english) {
                    "Some scales are copyrighted and may require permission for electronic redistribution. " +
                        "The app records your answers locally and does not upload them."
                } else {
                    "部分量表受版权保护，电子版再分发可能需要授权。应用只在本地保存答案，不会上传。" +
                        "如果你计划公开发布或商业使用，请逐项确认授权。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun ScaleCard(
    def: ScaleDef,
    latest: ScaleAssessmentEntity?,
    english: Boolean,
    compact: Boolean = false,
    onStart: () -> Unit
) {
    if (compact) {
        val band = latest?.let { def.band(it.severity) }
        SoftCard(onClick = onStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (english) def.nameEn else def.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (english) {
                            "${def.shortName} · ${def.questions.size} questions · about ${def.estimatedMinutes} min"
                        } else {
                            "${def.shortName} · ${def.questions.size} 题 · 约 ${def.estimatedMinutes} 分钟"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (latest != null) {
                        Text(
                            if (english) {
                                "Last: ${latest.score} / ${def.maxScore} · ${band?.let { def.bandLabel(it, true) } ?: latest.severity}"
                            } else {
                                "最近一次：${latest.score} / ${def.maxScore} · ${band?.let { def.bandLabel(it, false) } ?: latest.severity}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (english) def.nameEn else def.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    if (english) {
                        "${def.shortName} · ${def.categoryEn}"
                    } else {
                        "${def.shortName} · ${def.category}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            def.descriptionFor(english),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (english) {
                "${def.questions.size} questions · about ${def.estimatedMinutes} min"
            } else {
                "${def.questions.size} 题 · 约 ${def.estimatedMinutes} 分钟"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (latest != null) {
            val band = def.band(latest.severity)
            Spacer(Modifier.height(10.dp))
            Text(
                if (english) {
                    "Last: ${latest.score} / ${def.maxScore} · ${band?.let { def.bandLabel(it, true) } ?: latest.severity}"
                } else {
                    "最近一次：${latest.score} / ${def.maxScore} · ${band?.let { def.bandLabel(it, false) } ?: latest.severity}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                DateUtils.displayTimestamp(latest.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(
            text = if (latest == null) {
                if (english) "Start assessment" else "开始测评"
            } else {
                if (english) "Take it again" else "再测一次"
            },
            onClick = onStart
        )
    }
}

@Composable
private fun RecentScaleRow(
    item: ScaleAssessmentEntity,
    english: Boolean,
    onDelete: () -> Unit
) {
    val def = ScaleCatalog.byType(item.type)
    val band = def?.band(item.severity)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    def?.let { if (english) it.nameEn else it.shortName } ?: item.type,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${DateUtils.displayTimestamp(item.createdAt)} · ${item.score}${def?.let { " / ${it.maxScore}" } ?: ""}",
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
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除这条记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}
