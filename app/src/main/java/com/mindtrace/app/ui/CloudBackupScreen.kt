package com.mindtrace.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindtrace.app.data.CloudConfig
import com.mindtrace.app.data.CloudProvider
import com.mindtrace.app.ui.components.ConfirmDialog
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SecondaryActionButton
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text
import com.mindtrace.app.ui.i18n.translate
import kotlinx.coroutines.launch

private enum class CloudAction { UPLOAD, RESTORE }

/**
 * 云备份：把已有的加密备份（.mtrbackup）上传到 WebDAV / Gitee / GitHub，
 * 或从云端下载后复用本地「从备份恢复」的逻辑。
 *
 * 本地导出的加密备份、CSV、PDF 等原有功能完全不变；这里只是多了一个
 * 由用户手动触发的传输通道。
 */
@Composable
fun CloudBackupScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val initial = remember { viewModel.cloudConfig() }

    var provider by remember { mutableStateOf(initial.provider) }
    var webdavUrl by remember { mutableStateOf(initial.webdavUrl) }
    var webdavUser by remember { mutableStateOf(initial.webdavUser) }
    var webdavPassword by remember { mutableStateOf(initial.webdavPassword) }
    var gitOwner by remember { mutableStateOf(initial.gitOwner) }
    var gitRepo by remember { mutableStateOf(initial.gitRepo) }
    var gitBranch by remember { mutableStateOf(initial.gitBranch) }
    var gitPath by remember { mutableStateOf(initial.gitPath) }
    var gitToken by remember { mutableStateOf(initial.gitToken) }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var passwordAction by remember { mutableStateOf<CloudAction?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun currentConfig() = CloudConfig(
        provider = provider,
        webdavUrl = webdavUrl,
        webdavUser = webdavUser,
        webdavPassword = webdavPassword,
        gitOwner = gitOwner,
        gitRepo = gitRepo,
        gitBranch = gitBranch,
        gitPath = gitPath,
        gitToken = gitToken
    )

    fun persistConfig() {
        viewModel.saveCloudConfig(currentConfig())
        Toast.makeText(context, translate("云备份配置已保存", language), Toast.LENGTH_SHORT).show()
    }

    fun requireConfig(): CloudConfig? {
        val config = currentConfig()
        if (!config.ready) {
            Toast.makeText(context, translate("请先填写并保存云备份配置", language), Toast.LENGTH_SHORT).show()
            return null
        }
        viewModel.saveCloudConfig(config)
        return config
    }

    fun runUpload(password: CharArray) {
        val config = requireConfig() ?: return
        busy = true
        message = ""
        scope.launch {
            val result = viewModel.uploadCloudBackup(config, password)
            busy = false
            message = result.fold(
                onSuccess = { translate("云备份已上传", language) + " · " + it },
                onFailure = { translate("上传失败：", language) + (it.message ?: "") }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun runRestore(password: CharArray) {
        val config = requireConfig() ?: return
        busy = true
        message = ""
        scope.launch {
            val result = viewModel.restoreCloudBackup(config, password)
            busy = false
            message = result.fold(
                onSuccess = { translate("恢复完成", language) },
                onFailure = { translate("恢复失败：", language) + (it.message ?: "") }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "云备份",
            subtitle = "把加密备份上传到你自己的云端",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                InfoBanner(
                    text = "备份文件始终是加密的 .mtrbackup，与本地导出的格式完全一致。只有点击上传或下载时才会联网；连接凭据只保存在本机，备份密码不会被保存。"
                )
            }

            item {
                SoftCard {
                    SectionTitle("备份平台")
                    Spacer(Modifier.height(8.dp))
                    SegmentedOptions(
                        options = listOf(
                            CloudProvider.WEBDAV to "WebDAV",
                            CloudProvider.GITEE to "Gitee",
                            CloudProvider.GITHUB to "GitHub"
                        ),
                        selected = provider,
                        onSelect = { provider = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (provider) {
                            CloudProvider.WEBDAV ->
                                "填写完整的 HTTPS 文件地址（包含备份文件名），使用账号密码或应用专用密码。"
                            CloudProvider.GITEE ->
                                "备份会作为文件上传到 Gitee 仓库。私人令牌需要该仓库的写权限。"
                            CloudProvider.GITHUB ->
                                "备份会作为文件上传到 GitHub 仓库。令牌需要 Contents 读写权限；私有仓库更安全。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SoftCard {
                    SectionTitle("连接配置")
                    Spacer(Modifier.height(8.dp))
                    if (provider == CloudProvider.WEBDAV) {
                        ConfigField(
                            label = "WebDAV 文件地址",
                            value = webdavUrl,
                            onChange = { webdavUrl = it },
                            hint = "https://dav.example.com/MindTrace/mindtrace.mtrbackup"
                        )
                        ConfigField(label = "用户名", value = webdavUser, onChange = { webdavUser = it })
                        ConfigField(
                            label = "密码 / 应用专用密码",
                            value = webdavPassword,
                            onChange = { webdavPassword = it },
                            secret = true
                        )
                    } else {
                        ConfigField(label = "仓库所有者", value = gitOwner, onChange = { gitOwner = it })
                        ConfigField(label = "仓库名", value = gitRepo, onChange = { gitRepo = it })
                        ConfigField(label = "分支", value = gitBranch, onChange = { gitBranch = it })
                        ConfigField(label = "文件路径", value = gitPath, onChange = { gitPath = it })
                        ConfigField(
                            label = "访问令牌",
                            value = gitToken,
                            onChange = { gitToken = it },
                            secret = true
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "密码与令牌由系统密钥库加密后仅存本机；应用不会把凭据或数据发送到其他地方。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SoftCard {
                    SectionTitle("上传与恢复")
                    Spacer(Modifier.height(10.dp))
                    SecondaryActionButton(
                        text = "保存配置",
                        enabled = !busy,
                        onClick = { persistConfig() }
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "上传备份到云端",
                        enabled = !busy,
                        onClick = {
                            if (requireConfig() != null) {
                                passwordText = ""
                                passwordAction = CloudAction.UPLOAD
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryActionButton(
                        text = "从云端下载并恢复",
                        enabled = !busy,
                        onClick = {
                            if (requireConfig() != null) confirmRestore = true
                        }
                    )
                    if (busy || message.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (busy) translate("正在处理…", language) else message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            viewModel.clearCloudConfig()
                            webdavUrl = ""
                            webdavUser = ""
                            webdavPassword = ""
                            gitOwner = ""
                            gitRepo = ""
                            gitToken = ""
                            message = ""
                            Toast.makeText(
                                context,
                                translate("已清除云备份配置", language),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("清除已保存的连接配置")
                    }
                }
            }

            item {
                Text(
                    "云端文件即使被他人看到也只是加密备份，无法在没有密码的情况下读取。忘记密码时这份备份无法恢复；更换手机前请先确认云端备份可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "从云端恢复？",
            text = "下载云端备份会覆盖当前全部记录、药物、量表、日记与安全计划。建议先上传一次当前数据。",
            confirmLabel = "继续",
            destructive = true,
            onConfirm = {
                confirmRestore = false
                passwordText = ""
                passwordAction = CloudAction.RESTORE
            },
            onDismiss = { confirmRestore = false }
        )
    }

    if (passwordAction != null) {
        val action = passwordAction!!
        AlertDialog(
            onDismissRequest = { passwordAction = null },
            title = {
                Text(if (action == CloudAction.UPLOAD) "设置备份密码" else "输入备份密码")
            },
            text = {
                Column {
                    Text(
                        if (action == CloudAction.UPLOAD) {
                            "上传的是加密备份。密码不会保存在应用里，忘记后无法恢复。"
                        } else {
                            "输入上传时使用的密码。恢复会覆盖当前全部数据。"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passwordText.isNotBlank() && !busy,
                    onClick = {
                        val password = passwordText.toCharArray()
                        passwordText = ""
                        passwordAction = null
                        if (action == CloudAction.UPLOAD) runUpload(password) else runRestore(password)
                    }
                ) {
                    Text(if (action == CloudAction.UPLOAD) "上传" else "下载并恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordText = ""; passwordAction = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String? = null,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (hint != null) Text(hint, maxLines = 1) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password)
        else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}
