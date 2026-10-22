package com.mindtrace.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.WarmBackground
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import kotlinx.coroutines.launch

@Composable
fun ScaleScreen(
    viewModel: AppViewModel,
    type: String,
    onBack: () -> Unit,
    onOpenCrisis: () -> Unit
) {
    val def = remember(type) { ScaleCatalog.byType(type) }
    if (def == null) {
        WarmBackground {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                ScreenHeader(title = "未知量表", onBack = onBack)
                Text("这个量表暂不可用。", modifier = Modifier.padding(20.dp))
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val english = LocalAppLanguage.current.isEnglish
    var index by remember(type) { mutableIntStateOf(0) }
    var answers by remember(type) { mutableStateOf(List(def.questions.size) { -1 }) }
    var result by remember(type) { mutableStateOf<ScaleAssessmentEntity?>(null) }
    var submitting by remember(type) { mutableStateOf(false) }

    val currentAnswer = answers.getOrElse(index) { -1 }
    val isLast = index == def.questions.lastIndex

    fun submit() {
        if (answers.any { it < 0 } || submitting) return
        submitting = true
        scope.launch {
            result = viewModel.submitScale(def.type, answers)
            submitting = false
        }
    }

    WarmBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = def.shortName,
                subtitle = if (result == null) {
                    if (english) {
                        "${def.timeframeFor(true)} · Question ${index + 1} of ${def.questions.size}"
                    } else {
                        "${def.timeframeFor(false)} · 第 ${index + 1} / ${def.questions.size} 题"
                    }
                } else {
                    "测评结果"
                },
                onBack = onBack
            )
            if (result == null) {
                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { (index + 1f) / def.questions.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                def.instructionFor(english),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            SoftCard {
                                Text(
                                    def.questionText(index, english),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(18.dp))
                                def.questions[index].options.forEachIndexed { optionIndex, _ ->
                                    val selected = currentAnswer == optionIndex
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                answers = answers.toMutableList().also {
                                                    it[index] = optionIndex
                                                }
                                            },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selected,
                                                onClick = {
                                                    answers = answers.toMutableList().also {
                                                        it[index] = optionIndex
                                                    }
                                                }
                                            )
                                            Text(
                                                def.optionLabel(index, optionIndex, english),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (index == (def.selfHarmQuestionIndex ?: -1) && currentAnswer > 0) {
                            item {
                                InfoBanner(
                                    text = "这道题的回答让人担心。如果你正在经历伤害自己的念头，请现在就联系信任的人、心理援助热线 12356，或打开紧急支持。",
                                    icon = Icons.Rounded.Favorite,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = onOpenCrisis, contentPadding = PaddingValues(0.dp)) {
                                    Text("打开紧急支持")
                                }
                            }
                        }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (index > 0) {
                            TextButton(
                                onClick = { index -= 1 },
                                modifier = Modifier.heightIn(min = 52.dp)
                            ) { Text("上一题") }
                        }
                        PrimaryActionButton(
                            text = when {
                                !isLast -> "下一题"
                                submitting -> "保存中…"
                                else -> "查看结果"
                            },
                            modifier = Modifier.weight(1f),
                            enabled = currentAnswer >= 0 && !submitting,
                            onClick = {
                                if (isLast) submit() else index += 1
                            }
                        )
                    }
                }
            } else {
                ResultContent(
                    defType = def.type,
                    english = english,
                    result = result!!,
                    onDone = onBack,
                    onRetry = {
                        result = null
                        index = 0
                        answers = List(def.questions.size) { -1 }
                    },
                    onOpenCrisis = onOpenCrisis
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    defType: String,
    english: Boolean,
    result: ScaleAssessmentEntity,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onOpenCrisis: () -> Unit
) {
    val def = ScaleCatalog.byType(defType)
    val band = def?.band(result.severity)
    val answers = result.answers.split(',').mapNotNull { it.toIntOrNull() }
    val selfHarm = def?.selfHarmQuestionIndex?.let { idx -> answers.getOrNull(idx) } ?: 0
    val detail = def?.detail(answers, english)
    val source = def?.sourceFor(english).orEmpty()
    val note = def?.noteFor(english).orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${result.score}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (english) "Out of ${def?.maxScore ?: "?"}" else "满分 ${def?.maxScore ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        band?.let { def?.bandLabel(it, english) } ?: result.severity,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        item {
            SoftCard {
                Text(
                    def?.nameFor(english) ?: defType,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    band?.let { def?.bandAdvice(it, english) }
                        ?: if (english) "Sharing this result with a professional may be helpful."
                        else "把这次结果带给专业人员讨论，会更有帮助。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (selfHarm > 0) {
            item {
                SoftCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("你不需要独自面对", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (english) {
                                    "Your answer to question ${(def?.selfHarmQuestionIndex ?: 0) + 1} suggests you may be having thoughts of harming yourself. Tell someone you trust, or contact 12356 / 120 / 110."
                                } else {
                                    "第 ${(def?.selfHarmQuestionIndex ?: 0) + 1} 题的回答提示你可能正在经历伤害自己的念头。请告诉信任的人，或联系 12356 / 120 / 110。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onOpenCrisis, contentPadding = PaddingValues(0.dp)) {
                                Text("紧急支持")
                            }
                        }
                    }
                }
            }
        }
        if (!note.isNullOrBlank()) {
            item {
                InfoBanner(
                    text = note,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        item {
            InfoBanner(
                text = "这个分数只是一个筛查参考，不是诊断。结果已保存在本机，会出现在趋势和复诊报告里。",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        if (source.isNotBlank()) {
            item {
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
        item {
            PrimaryActionButton(text = "完成", onClick = onDone)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  再测一次")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
