package com.mindtrace.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SoftCard
import com.mindtrace.app.ui.components.WarmBackground
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.Text

private const val SPONSOR_URL = "https://ifdian.net/a/Hi-res"

private val authorNoteZh = AuthorNote.zh
private val authorNoteEn = AuthorNote.en

@Composable
fun SponsorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val english = LocalAppLanguage.current.isEnglish

    fun copyLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MindTrace sponsor", SPONSOR_URL))
        Toast.makeText(
            context,
            if (english) "Link copied" else "链接已复制",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun openBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            copyLink()
        }
    }

    WarmBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "赞助与作者的话",
                subtitle = "如果心迹对你有帮助，欢迎支持它继续更新",
                onBack = onBack
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.VolunteerActivism,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "关于赞助",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (english) {
                                "MindTrace has no ads, no tracking and no network permission. Sponsorship is optional and only supports maintenance and new features; nothing in the app is locked behind it."
                            } else {
                                "心迹没有广告、没有追踪，也没有网络权限。赞助完全自愿，只会用于维护与继续开发；App 里没有任何功能需要付费解锁。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryActionButton(
                            text = if (english) "Support on Afdian" else "在爱发电上赞助",
                            onClick = { openBrowser() }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { copyLink() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (english) "Copy link" else "复制链接")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            SPONSOR_URL,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (english) "Opens in your default browser." else "点击右上按钮会用系统浏览器打开。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("作者的话", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        val paragraphs = if (english) authorNoteEn else authorNoteZh
                        paragraphs.forEachIndexed { index, paragraph ->
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (index != paragraphs.lastIndex) {
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
