package com.mindtrace.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.report.ReportData
import com.mindtrace.app.report.ReportLayout
import com.mindtrace.app.report.ReportOptions
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.i18n.translate
import com.mindtrace.app.ui.components.DateField
import com.mindtrace.app.ui.components.InfoBanner
import com.mindtrace.app.ui.components.MetricTile
import com.mindtrace.app.ui.components.PrimaryActionButton
import com.mindtrace.app.ui.components.ScreenHeader
import com.mindtrace.app.ui.components.SecondaryActionButton
import com.mindtrace.app.ui.components.SectionTitle
import com.mindtrace.app.ui.components.SegmentedOptions
import com.mindtrace.app.ui.components.SoftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ReportScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    var previewPages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    val defaultRange = remember(settings.defaultReportDays) {
        DateUtils.rangeForDays(settings.defaultReportDays)
    }
    var startDate by rememberSaveable { mutableStateOf(defaultRange.first) }
    var endDate by rememberSaveable { mutableStateOf(defaultRange.second) }
    var includeJournals by rememberSaveable { mutableStateOf(false) }
    var questions by rememberSaveable { mutableStateOf("") }
    var layoutName by rememberSaveable { mutableStateOf(ReportLayout.CLINICAL.name) }
    var reportData by remember { mutableStateOf<ReportData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val dateValid = startDate <= endDate
    val layout = ReportLayout.valueOf(layoutName)

    val options = ReportOptions(
        includeJournals = includeJournals,
        includeNotes = true,
        doctorQuestions = questions,
        layout = layout
    )

    fun reportFileName(): String {
        val suffix = if (layout == ReportLayout.CLINICAL) "clinical" else "summary"
        return "MindTrace-Report-$startDate-$endDate-$suffix.pdf"
    }

    LaunchedEffect(startDate, endDate, options) {
        if (!dateValid) {
            reportData = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(200)
        reportData = runCatching { viewModel.buildReport(startDate, endDate, options) }.getOrNull()
        loading = false
    }

    fun toast(message: String) {
        Toast.makeText(context, translate(message, language), Toast.LENGTH_SHORT).show()
    }

    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val data = reportData ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        previewPages = null
        scope.launch {
            busy = true
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    viewModel.writeReportPdf(data, output, language)
                } ?: error(translate("无法写入所选位置", language))
            }.onSuccess {
                toast("PDF 已保存")
            }.onFailure {
                toast(translate("保存失败：", language) + (it.message ?: translate("未知错误", language)))
            }
            busy = false
        }
    }

    fun sharePdf() {
        val data = reportData ?: return
        scope.launch {
            busy = true
            runCatching {
                val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                val file = File(dir, reportFileName())
                file.outputStream().use { output -> viewModel.writeReportPdf(data, output, language) }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        translate("复诊记录", language) +
                            " ${DateUtils.displayDate(startDate)} — ${DateUtils.displayDate(endDate)}"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, "分享复诊记录"))
            }.onFailure {
                toast(
                    translate("生成分享文件失败：", language) +
                        (it.message ?: translate("未知错误", language))
                )
            }
            busy = false
        }
    }

    fun openPreview() {
        val data = reportData ?: return
        scope.launch {
            previewLoading = true
            runCatching {
                val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                val file = File(dir, "MindTrace-Preview.pdf")
                file.outputStream().use { output -> viewModel.writeReportPdf(data, output, language) }
                withContext(Dispatchers.IO) { renderPdfPages(file) }
            }.onSuccess { previewPages = it }
                .onFailure {
                    toast(
                        translate("生成预览失败：", language) +
                            (it.message ?: translate("未知错误", language))
                    )
                }
            previewLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "复诊报告",
                subtitle = "把自述记录整理成医生容易阅读的报告",
                onBack = onBack
            )
        }
        item {
            InfoBanner(
                text = "报告包含情绪、症状、用药等敏感信息。保存或分享前，请确认接收对象；心迹不会自动上传任何内容。",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        item {
            SoftCard {
                SectionTitle("时间范围")
                Spacer(Modifier.height(10.dp))
                SegmentedOptions(
                    options = listOf(7 to "7 天", 14 to "14 天", 30 to "30 天", 90 to "90 天"),
                    selected = when {
                        daysBetween(startDate, endDate) == 7 -> 7
                        daysBetween(startDate, endDate) == 14 -> 14
                        daysBetween(startDate, endDate) == 90 -> 90
                        else -> 30
                    },
                    onSelect = { preset ->
                        val range = DateUtils.rangeForDays(preset)
                        startDate = range.first
                        endDate = range.second
                    }
                )
                Spacer(Modifier.height(12.dp))
                DateField(label = "开始日期", isoDate = startDate, onDateChange = { startDate = it })
                Spacer(Modifier.height(10.dp))
                DateField(label = "结束日期", isoDate = endDate, onDateChange = { endDate = it })
                if (!dateValid) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "开始日期不能晚于结束日期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        item {
            SoftCard {
                SectionTitle("报告版本", subtitle = "预览、保存和分享都会使用所选版本")
                Spacer(Modifier.height(10.dp))
                SegmentedOptions(
                    options = listOf(
                        ReportLayout.CLINICAL to "医生详细版",
                        ReportLayout.COMPACT to "一页摘要"
                    ),
                    selected = layout,
                    onSelect = { layoutName = it.name }
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    if (layout == ReportLayout.CLINICAL) {
                        "约 3–5 页：第一页为摘要，症状图使用两列大图，适合打印和医生阅读。"
                    } else {
                        "通常 1 页：三列紧凑症状图，适合自己留存或快速分享。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("包含日记摘录", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "默认不包含，避免把不想分享的内容带进报告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includeJournals, onCheckedChange = { includeJournals = it })
                }
            }
        }
        item {
            SoftCard {
                SectionTitle("想和医生讨论的问题", subtitle = "会写在报告末尾")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = questions,
                    onValueChange = { questions = it },
                    placeholder = { Text("例如：最近加量后手抖明显，是否需要调整？") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }
        item {
            SoftCard {
                SectionTitle("报告预览")
                Spacer(Modifier.height(10.dp))
                if (loading) {
                    Text("正在整理…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (reportData == null) {
                    Text("选择一个有效的时间范围后生成预览。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val data = reportData!!
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(label = "记录", value = "${data.recordedDays}", suffix = " 天", modifier = Modifier.weight(1f))
                        MetricTile(label = "症状", value = "${data.symptomFrequency.size}", suffix = " 项", modifier = Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                        MetricTile(label = "量表", value = "${data.scales.size}", suffix = " 次", modifier = Modifier.weight(1f), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "平均情绪 ${data.averageMood?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} / 5 · " +
                            "平均睡眠 ${data.averageSleep?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} 小时 · " +
                            "药物 ${data.medications.size} 种",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (data.adherence.total > 0) "按时服药率 ${data.adherence.percent}%" else "按时服药率：暂无定时用药记录") +
                            " · 伤害自己的念头记录 ${data.ideationEvents.size} 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SecondaryActionButton(
                text = if (previewLoading) "正在生成预览…" else "预览 PDF",
                enabled = !loading && reportData != null && !busy && !previewLoading,
                onClick = { openPreview() }
            )
            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (busy) "正在生成…" else "保存 PDF",
                enabled = !loading && reportData != null && !busy,
                onClick = {
                    createPdf.launch(reportFileName())
                }
            )
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(
                text = "分享 PDF",
                enabled = !loading && reportData != null && !busy,
                onClick = { sharePdf() }
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    "  PDF 由本机生成，不经过网络。就诊时可以直接给医生看，也可以打印。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (previewLoading) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC101010)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Text("正在生成预览…", color = Color.White)
                }
            }
        }
    }

    previewPages?.let { pages ->
        var pageIndex by remember(pages) { mutableIntStateOf(0) }
        var zoom by remember(pages) { mutableFloatStateOf(1f) }
        var offset by remember(pages) { mutableStateOf(Offset.Zero) }
        var viewport by remember { mutableStateOf(IntSize.Zero) }

        fun resetView() {
            zoom = 1f
            offset = Offset.Zero
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
            zoom = newZoom
            if (newZoom <= 1.01f) {
                offset = Offset.Zero
            } else {
                val maxX = viewport.width * (newZoom - 1f) / 2f
                val maxY = viewport.height * (newZoom - 1f) / 2f
                offset = Offset(
                    (offset.x + panChange.x).coerceIn(-maxX, maxX),
                    (offset.y + panChange.y).coerceIn(-maxY, maxY)
                )
            }
        }

        Dialog(
            onDismissRequest = { previewPages = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000))
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.88f),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1C1C1C),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (language.isEnglish) "PDF preview" else "PDF 预览",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (language.isEnglish) {
                                        "Pinch to zoom · drag to pan"
                                    } else {
                                        "双指缩放 · 拖动查看"
                                    },
                                    color = Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { previewPages = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 14.dp)
                                .clipToBounds()
                                .onSizeChanged { viewport = it }
                                .transformable(transformState)
                                .background(Color(0xFF0F0F0F), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = pages[pageIndex].asImageBitmap(),
                                contentDescription = "第 ${pageIndex + 1} 页",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.TextButton(
                                enabled = pageIndex > 0,
                                onClick = {
                                    pageIndex--
                                    resetView()
                                }
                            ) {
                                Text(
                                    if (language.isEnglish) "Previous" else "上一页",
                                    color = if (pageIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${pageIndex + 1} / ${pages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.weight(1f))
                            androidx.compose.material3.TextButton(
                                enabled = pageIndex < pages.lastIndex,
                                onClick = {
                                    pageIndex++
                                    resetView()
                                }
                            ) {
                                Text(
                                    if (language.isEnglish) "Next" else "下一页",
                                    color = if (pageIndex < pages.lastIndex) Color.White
                                    else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    zoom = (zoom / 1.25f).coerceAtLeast(1f)
                                    if (zoom <= 1f) offset = Offset.Zero
                                }
                            ) { Text("−", color = Color.White) }
                            Text(
                                "${(zoom * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    zoom = (zoom * 1.25f).coerceAtMost(4f)
                                    offset = Offset.Zero
                                }
                            ) { Text("+", color = Color.White) }
                            androidx.compose.material3.TextButton(onClick = { resetView() }) {
                                Text(
                                    if (language.isEnglish) "Reset" else "重置",
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 30.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = { createPdf.launch(reportFileName()) },
                                modifier = Modifier.weight(1f)
                            ) { Text("保存 PDF", color = Color.White) }
                            androidx.compose.material3.TextButton(
                                onClick = { sharePdf() },
                                modifier = Modifier.weight(1f)
                            ) { Text("分享 PDF", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

private fun renderPdfPages(file: File): List<Bitmap> {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        PdfRenderer(descriptor).use { renderer ->
            val result = ArrayList<Bitmap>(renderer.pageCount)
            val scale = 2f
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                result.add(bitmap)
            }
            return result
        }
    } finally {
        descriptor.close()
    }
}

private fun daysBetween(start: String, end: String): Int {
    val s = DateUtils.parse(start) ?: return 0
    val e = DateUtils.parse(end) ?: return 0
    return java.time.temporal.ChronoUnit.DAYS.between(s, e).toInt() + 1
}
