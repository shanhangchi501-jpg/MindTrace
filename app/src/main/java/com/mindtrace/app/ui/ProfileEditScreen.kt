package com.mindtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.Pill
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard
import java.time.LocalDate

private val genderOptions = listOf("女", "男", "非二元", "不透露")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var initialized by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        if (!initialized && profile != null) {
            name = profile!!.displayName
            birthYear = profile!!.birthYear?.toString() ?: ""
            gender = profile!!.gender
            conditions = profile!!.conditions.split(',').filter { it.isNotBlank() }.toSet()
            note = profile!!.note
            initialized = true
        }
    }

    val yearValue = birthYear.toIntOrNull()
    val yearValid = birthYear.isBlank() || (yearValue != null && yearValue in 1900..LocalDate.now().year)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(
            title = "我的资料",
            subtitle = "都可以留空；这些信息只会写进你主动生成的报告",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SoftCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("称呼 / 化名") },
                        placeholder = { Text("可以不是真实姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = birthYear,
                        onValueChange = { birthYear = it.filter(Char::isDigit).take(4) },
                        label = { Text("出生年份（可选）") },
                        placeholder = { Text("例如：1998") },
                        singleLine = true,
                        isError = !yearValid,
                        supportingText = { if (!yearValid) Text("请输入有效的年份") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("性别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        genderOptions.forEach { option ->
                            Pill(
                                text = option,
                                selected = gender == option,
                                onClick = { gender = if (gender == option) "" else option }
                            )
                        }
                    }
                }
            }
            item {
                SoftCard {
                    SectionTitle("关注方向", subtitle = "用于推荐症状选项，不会写进诊断")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SymptomCatalog.conditions.forEach { condition ->
                            Pill(
                                text = condition.label,
                                selected = condition.key in conditions,
                                onClick = {
                                    conditions = if (condition.key in conditions) {
                                        conditions - condition.key
                                    } else {
                                        conditions + condition.key
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item {
                SoftCard {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("其他想补充的背景（可选）") },
                        placeholder = { Text("例如：目前的诊断、正在接受的治疗、想提醒医生的信息") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
            item {
                InfoBanner(
                    text = "资料会加密保存在本机。生成复诊报告时，称呼、年龄、性别和关注方向会写入报告首页；如果你不想分享，可以留空。"
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
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
                    enabled = yearValid,
                    onClick = {
                        viewModel.updateProfile(
                            displayName = name,
                            birthYear = yearValue,
                            gender = gender,
                            conditions = conditions.toList(),
                            note = note
                        )
                        onBack()
                    }
                )
            }
        }
    }
}
