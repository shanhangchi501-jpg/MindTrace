package com.mindtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SoftCard

@Composable
fun TermsScreen(onBack: () -> Unit) {
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
                title = "使用条款",
                subtitle = "请在持续使用前阅读；这不是医疗建议",
                onBack = onBack
            )
        }
        item {
            InfoBanner(
                text = "心迹是自我记录工具，不是医疗器械，不提供诊断、处方或治疗建议，不能替代医生或心理咨询师。"
            )
        }
        item {
            TermsSection(
                title = "1. 服务性质",
                body = "心迹提供情绪、睡眠、症状、用药、自评量表和日记的记录与整理功能。所有内容由使用者本人填写，应用只做汇总和展示。"
            )
        }
        item {
            TermsSection(
                title = "2. 不提供医疗建议",
                body = "PHQ-9 与 GAD-7 是筛查工具，分数会随状态波动，不能用于自我诊断。任何药物、剂量或治疗调整，请与有资质的医生讨论。"
            )
        }
        item {
            TermsSection(
                title = "3. 提醒与安全边界",
                body = "提醒依赖系统通知、精确闹钟和后台调度，可能因系统版本、省电策略或厂商限制而延迟或失败。心迹不能作为唯一的安全监护工具。如果你或他人正处于危险中，请立即联系急救或前往最近的急诊，不要等待应用提醒。"
            )
        }
        item {
            TermsSection(
                title = "4. 数据与备份",
                body = "记录加密保存在本机。开发者无法访问、恢复或找回你的数据。卸载应用、清除应用数据或删除全部数据会销毁密钥，数据将无法恢复。PIN 和备份密码都没有找回入口。加密备份包含记录、药物、量表、日记、安全计划和联系人，但不包含 PIN、提醒与界面设置。请在更换手机或卸载前自行导出备份，并妥善保管密码。"
            )
        }
        item {
            TermsSection(
                title = "5. 分享责任",
                body = "复诊 PDF、CSV 和加密备份都包含高度敏感的个人信息。是否分享、分享给谁、保存在哪里，由你自行决定并承担相应风险。分享前请确认接收对象和设备环境。"
            )
        }
        item {
            TermsSection(
                title = "6. 未成年人",
                body = "如果你未满 18 岁，建议在监护人知情和同意的情况下使用，并在需要时寻求可信任成年人的帮助。"
            )
        }
        item {
            TermsSection(
                title = "7. 适用地区与紧急资源",
                body = "应用内的 120、110 和 12356 适用于中国大陆，12356 的接听时间以当地为准。如果你不在中国大陆，请使用当地急救电话和危机资源。"
            )
        }
        item {
            TermsSection(
                title = "8. 免责声明",
                body = "心迹按现状提供，不承诺不间断、无错误或适用于任何特定目的。在法律允许的范围内，开发者不对因使用或无法使用本应用造成的间接损失承担责任。本条款不排除法律规定不得排除的责任。"
            )
        }
        item {
            TermsSection(
                title = "9. 变更",
                body = "如果未来版本引入新的数据处理方式或功能，会在更新前说明。继续使用即表示你接受更新后的条款；如不接受，请停止使用并删除应用与数据。"
            )
        }
        item {
            Text(
                "本条款为通用说明，不构成法律意见。若你计划将本应用用于商业或医疗场景，请咨询专业人士并完成相应合规评估。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    SoftCard {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
