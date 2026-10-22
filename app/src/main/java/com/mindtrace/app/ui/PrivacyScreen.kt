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
fun PrivacyScreen(onBack: () -> Unit) {
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
                title = "隐私说明",
                subtitle = "简明、完整地说明心迹如何处理你的信息",
                onBack = onBack
            )
        }
        item {
            InfoBanner(
                text = "一句话版本：心迹没有服务器，不收集你的数据；记录加密保存在本机，只有你能通过设备访问。"
            )
        }
        item {
            SoftCard {
                SectionTitle("1. 不联网")
                Spacer(Modifier.height(6.dp))
                Body(
                    "心迹在 AndroidManifest 中刻意不申请 INTERNET 权限。因此应用本身没有能力向任何服务器发送数据，" +
                        "也不存在账号、登录、云端同步、广告或第三方统计 SDK。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("2. 数据保存在哪里")
                Spacer(Modifier.height(6.dp))
                Body(
                    "所有记录保存在本机的 SQLCipher 加密数据库中。数据库口令随机生成，" +
                        "由 Android Keystore 中的不可导出密钥加密后保存。应用锁 PIN 仅保存 PBKDF2 哈希，" +
                        "不保存明文。应用不参与 Android 云备份，也不允许设备迁移自动带走数据。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("3. 应用锁与截屏")
                Spacer(Modifier.height(6.dp))
                Body(
                    "你可以设置 4–6 位 PIN，并选择使用指纹或面容解锁。应用从后台返回时可自动锁定。" +
                        "「阻止截屏」会阻止系统截屏与最近任务预览，默认关闭，由你决定是否开启。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("4. 通知")
                Spacer(Modifier.height(6.dp))
                Body(
                    "提醒由本机系统闹钟与通知完成，不经过网络。通知默认脱敏，只显示「心迹」与通用提示，" +
                        "不会在锁屏上显示药名、症状或日记内容。你可以在设置中关闭脱敏或提醒。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("5. 导出与分享")
                Spacer(Modifier.height(6.dp))
                Body(
                    "PDF、CSV 和加密备份都由你主动生成，并保存到你通过系统文件选择器指定的位置。" +
                        "加密备份使用你自己设置的密码，通过 PBKDF2（20 万次）派生密钥并以 AES-256-GCM 加密。" +
                        "加密备份包含记录、药物、量表、日记、安全计划和联系人，但不包含 PIN、提醒和界面设置。" +
                        "一旦分享出去，接收方可能保存或转发，请在分享前确认对象。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("6. 删除")
                Spacer(Modifier.height(6.dp))
                Body(
                    "你可以在「设置 → 数据与报告 → 删除全部数据」中清除所有记录、药物、量表、日记、安全计划和设置。" +
                        "该操作会同时销毁数据库密钥，使旧数据无法恢复。卸载应用或清除应用数据也会销毁密钥。" +
                        "更换手机或卸载前请先自行导出加密备份；PIN 和备份密码都没有找回入口。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("7. 医疗边界")
                Spacer(Modifier.height(6.dp))
                Body(
                    "心迹不是医疗器械，不提供诊断、处方或治疗建议，也不能替代医生或心理咨询师。" +
                        "PHQ-9 与 GAD-7 是筛查工具，分数不能用于自我诊断。任何治疗调整，请与专业人员讨论。" +
                        "应用内的 120、110、12356 适用于中国大陆；如果你不在中国大陆，请拨打当地急救电话和危机资源。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("8. 未成年人")
                Spacer(Modifier.height(6.dp))
                Body(
                    "如果你未满 18 岁，建议在监护人知情和同意的情况下使用本应用，并在需要时寻求可信任成年人的帮助。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("9. 第三方组件")
                Spacer(Modifier.height(6.dp))
                Body(
                    "心迹使用 Kotlin、Jetpack Compose、AndroidX、Room、SQLCipher for Android 等开源组件。" +
                        "这些组件在本机运行，不包含用于收集个人数据的远程分析服务。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("10. 你的权利")
                Spacer(Modifier.height(6.dp))
                Body(
                    "由于开发者无法访问你的设备或数据，你对自己的记录拥有完整控制权：" +
                        "可以查看、修改、导出、备份、删除，也可以随时卸载应用。卸载后，由于 Keystore 密钥被销毁，" +
                        "残留的加密数据将无法再被读取。"
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("11. 权限说明")
                Spacer(Modifier.height(6.dp))
                Body(
                    "通知：用于每日记录和用药提醒，可随时在系统设置中关闭。\n\n" +
                        "精确提醒 / 闹钟：用于让提醒准时触发；未允许时应用会改用系统闹钟方式，状态栏可能出现闹钟图标。\n\n" +
                        "生物识别：仅用于应用解锁，指纹和面容数据由 Android 系统处理，应用不会读取或保存。\n\n" +
                        "开机启动：用于重启后恢复已设置的提醒。\n\n" +
                        "网络：应用不申请 INTERNET 权限，也不会读取网络状态。"
                )
            }
        }
        item {
            Text(
                "如对隐私说明有疑问，请在使用前先确认你是否接受以上安排。本说明适用于心迹的所有本地功能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
