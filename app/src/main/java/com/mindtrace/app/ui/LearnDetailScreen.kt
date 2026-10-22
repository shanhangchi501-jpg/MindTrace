package com.mindtrace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindtrace.app.data.EducationBlock
import com.mindtrace.app.data.EducationCatalog
import com.mindtrace.app.data.EducationEntry
import com.mindtrace.app.ui.components.EmptyState
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

@Composable
fun LearnDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val english = LocalAppLanguage.current.isEnglish
    val data = remember { EducationCatalog.load(context) }
    val entry = remember(data, entryId) { data.entries.firstOrNull { it.id == entryId } }

    if (entry == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            EmptyState(
                title = if (english) "Entry not found" else "条目不存在",
                body = if (english) "This entry may have been removed." else "这条内容可能已被移除。"
            )
        }
        return
    }

    val blocks = entry.displayBlocks(english)
    val chineseOnly = english && !entry.hasEnglishContent()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp, end = 4.dp)
                ) {
                    Text(
                        EducationCatalog.categoryLabel(entry.category, english),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.displayTitle(english),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${if (english) entry.sourceLabel() else entry.source} · ${entry.license}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.updated.isNotBlank()) {
                        Text(
                            (if (english) "Updated " else "更新于 ") + entry.updated,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (entry.displayDescription(english).isNotBlank()) {
            item {
                SoftCard {
                    Text(
                        entry.displayDescription(english),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }

        if (chineseOnly) {
            item {
                InfoBanner(
                    text = "This entry is currently available in Chinese only. The original Chinese text is shown below.",
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        item {
            InfoBanner(
                text = if (english) {
                    "This content may discuss trauma, self-harm, dissociation or other sensitive topics. Take care of yourself while reading."
                } else {
                    "内容可能涉及创伤、自伤、解离等敏感话题，请留意自身状态，必要时暂停阅读。"
                },
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(14.dp))
        }

        if (entry.hasTable(english)) {
            item {
                Text(
                    if (english) "Tables can be scrolled horizontally."
                    else "表格内容可以左右滑动查看。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        item {
            SelectionContainer {
                EducationBlocks(blocks)
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    if (english) "Source & license" else "来源与许可",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${if (english) entry.sourceLabel() else entry.source} · ${entry.displayTitle(english)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    entry.sourceUrlFor(english),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "CC BY-SA 4.0 · https://creativecommons.org/licenses/by-sa/4.0/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.truncated) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (english) "This entry is shortened. Open the source link for the full text."
                        else "本条目有节选，完整内容请查看来源链接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (english) {
                        "For health education only. Not diagnosis, prescription or medication advice. For diagnosis and treatment, consult a qualified clinician."
                    } else {
                        "仅供健康教育，不构成诊断、处方或用药建议。诊断、用药和治疗方案请咨询具备资质的专业人员。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (english) {
                        "If you are in danger, call 120 / 110 or the mental health hotline 12356 (mainland China)."
                    } else {
                        "如果你正处于危险中，请拨打 120 / 110，或心理援助热线 12356（中国大陆）。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EducationBlocks(blocks: List<EducationBlock>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block.type) {
                "heading" -> EducationHeading(block)
                "paragraph" -> EducationParagraph(block.text)
                "bullets" -> EducationBullets(block.items, ordered = false)
                "ordered" -> EducationBullets(block.items, ordered = true)
                "quote" -> EducationQuote(block.text)
                "notice" -> EducationNotice(block)
                "table" -> EducationTable(block)
                else -> if (block.text.isNotBlank()) EducationParagraph(block.text)
            }
        }
    }
}

@Composable
private fun EducationHeading(block: EducationBlock) {
    val level = block.level.coerceAtLeast(2)
    val (top, bottom) = when (level) {
        2 -> 20.dp to 8.dp
        3 -> 16.dp to 6.dp
        else -> 12.dp to 4.dp
    }
    Text(
        block.text,
        style = when (level) {
            2 -> MaterialTheme.typography.titleMedium
            3 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.labelLarge
        },
        fontWeight = FontWeight.SemiBold,
        color = when (level) {
            2 -> MaterialTheme.colorScheme.primary
            3 -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(top = top, bottom = bottom)
    )
}

@Composable
private fun EducationParagraph(text: String) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 23.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun EducationBullets(items: List<String>, ordered: Boolean) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    if (ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(if (ordered) 26.dp else 16.dp)
                )
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EducationQuote(text: String) {
    if (text.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (text.length > 60) 60.dp else 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 23.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
}

@Composable
private fun EducationNotice(block: EducationBlock) {
    SoftCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)) {
        if (block.title.isNotBlank()) {
            Text(
                block.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            block.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            lineHeight = 20.sp
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EducationTable(block: EducationBlock) {
    if (block.headers.isEmpty()) return
    val scroll = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
        ) {
            Row(modifier = Modifier.background(headerColor)) {
                block.headers.forEach { header ->
                    TableCell(header, header = true)
                }
            }
            block.rows.forEach { row ->
                HorizontalDivider(color = borderColor)
                Row {
                    block.headers.indices.forEach { index ->
                        TableCell(row.getOrElse(index) { "" }, header = false)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TableCell(text: String, header: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        lineHeight = 18.sp,
        modifier = Modifier
            .width(148.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
