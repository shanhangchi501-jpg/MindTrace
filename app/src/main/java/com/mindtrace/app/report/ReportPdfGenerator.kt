package com.mindtrace.app.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mindtrace.app.data.AppLanguage
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DiagnosisCatalog
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.data.SafetyCatalog
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.data.displayLabel
import com.mindtrace.app.data.displayName
import com.mindtrace.app.data.parseSymptomSeverity
import com.mindtrace.app.ui.i18n.translate
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * 复诊摘要。默认压缩为一页；症状强度图超过 11 张或后面区块空间不足时自动续页。
 * 勾选“包含日记摘录”且有日记时，再追加日记页。
 * 通过固定版式、列表截断和紧凑图表保证正常报告不溢出。
 */
object ReportPdfGenerator {

    private const val FIRST_PAGE_SYMPTOM_CHARTS = 11
    private const val SYMPTOM_CHARTS_PER_CONTINUATION_PAGE = 12
    private const val SYMPTOM_COLUMNS = 3
    private const val CLINICAL_SYMPTOM_COLUMNS = 2
    private const val CLINICAL_CHARTS_PER_PAGE = 6

    fun write(data: ReportData, output: OutputStream, language: AppLanguage) {
        val document = PdfDocument()
        Writer(document, data, language).render()
        document.writeTo(output)
        document.close()
    }

    private class Writer(
        private val document: PdfDocument,
        private val data: ReportData,
        private val language: AppLanguage
    ) {
        private val english = language.isEnglish
        private val width = 595
        private val height = 842
        private val margin = 34f
        private val contentRight = width - margin
        private val contentWidth = contentRight - margin
        private val footerY = height - 22f

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = margin
        private var pageNumber = 0

        private val ink = Color.rgb(43, 51, 46)
        private val muted = Color.rgb(104, 111, 105)
        private val accent = Color.rgb(101, 138, 116)
        private val accentDark = Color.rgb(58, 82, 66)
        private val accentSoft = Color.rgb(231, 238, 232)
        private val clay = Color.rgb(184, 131, 94)
        private val claySoft = Color.rgb(246, 235, 225)
        private val blue = Color.rgb(112, 137, 168)
        private val blueSoft = Color.rgb(232, 238, 245)
        private val cream = Color.rgb(249, 246, 240)
        private val lineColor = Color.rgb(226, 221, 211)
        private val white = Color.WHITE

        fun render() {
            when (data.options.layout) {
                ReportLayout.CLINICAL -> renderClinical()
                ReportLayout.COMPACT -> renderCompact()
            }
        }

        private fun renderCompact() {
            newPage(first = true)
            drawHeader()
            drawProfileAndOverview()
            drawDiagnoses()
            drawTrends()
            drawSymptomCharts()
            drawMedicationsAndScales()
            drawSafety()
            drawQuestions()
            drawDisclaimer()
            finishPage()

            if (data.options.includeJournals && data.journals.isNotEmpty()) {
                newPage(first = false)
                drawJournals()
                finishPage()
            }
        }

        /**
         * Doctor-facing report: a readable summary page, a tabular overview
         * page, then large two-column symptom charts across as many pages as
         * needed.
         */
        private fun renderClinical() {
            newPage(first = true)
            drawHeader()
            drawProfileAndOverview()
            drawClinicalSummaryPoints()
            drawDiagnosesDetailed()
            drawMedicationSummary()
            drawSafety()
            drawQuestions()
            finishPage()

            newPage(first = false)
            drawSymptomOverview()
            drawMedicationDetails()
            drawScalesDetailed()
            drawClinicalDataNotes()

            val charts = data.symptomStats.filter { it.averageSeverity != null }
            if (charts.isEmpty()) {
                drawDisclaimer()
                finishPage()
            } else {
                finishPage()
                drawClinicalSymptomCharts(charts)
            }

            if (data.options.includeJournals && data.journals.isNotEmpty()) {
                newPage(first = false)
                drawJournals()
                finishPage()
            }
        }

        private fun newPage(first: Boolean) {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
            val newPage = document.startPage(info)
            page = newPage
            canvas = newPage.canvas
            canvas?.drawColor(white)
            y = margin
            if (!first) {
                val c = canvas ?: return
                drawText(if (english) "MindTrace" else "心迹", margin, y + 6f, 8f, accent, bold = true)
                drawText(if (english) "Visit report" else "复诊记录", margin + 58f, y + 6f, 8f, muted)
                val range = "${DateUtils.displayShort(data.startDate)} – ${DateUtils.displayShort(data.endDate)}"
                val p = paint(7.5f, muted)
                c.drawText(range, contentRight - p.measureText(range), y + 6f, p)
                c.drawRect(margin, y + 13f, contentRight, y + 13.7f, fill(lineColor))
                y += 24f
            }
        }

        private fun finishPage() {
            val c = canvas ?: return
            c.drawRect(margin, height - 34f, contentRight, height - 33.4f, fill(lineColor))
            val p = paint(6.8f, muted)
            val left = if (english) {
                "MindTrace · For personal tracking and clinical communication only"
            } else {
                "心迹 · 仅用于自我记录与就诊沟通"
            }
            c.drawText(left, margin, footerY, p)
            val number = if (english) "Page $pageNumber" else "第 $pageNumber 页"
            c.drawText(number, contentRight - p.measureText(number), footerY, p)
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        private fun drawHeader() {
            val c = canvas ?: return
            c.drawRoundRect(RectF(margin, y, contentRight, y + 68f), 12f, 12f, fill(accentDark))
            c.drawCircle(contentRight - 34f, y + 18f, 38f, fill(Color.argb(24, 255, 255, 255)))
            c.drawCircle(contentRight - 34f, y + 18f, 22f, fill(Color.argb(18, 255, 255, 255)))
            drawText(if (english) "MINDTRACE" else "心迹", margin + 18f, y + 16f, 8f, Color.rgb(207, 222, 211), bold = true)
            drawText(if (english) "Visit report" else "复诊记录", margin + 18f, y + 39f, 20f, white, bold = true)
            val range = if (english) {
                "${DateUtils.displayDate(data.startDate)} — ${DateUtils.displayDate(data.endDate)}"
            } else {
                "${DateUtils.displayFullDate(data.startDate)} — ${DateUtils.displayFullDate(data.endDate)}"
            }
            drawText(range, margin + 18f, y + 54f, 7.8f, Color.rgb(230, 238, 231))
            drawText(
                (if (english) "Generated " else "生成于 ") + DateUtils.displayTimestamp(data.generatedAt),
                margin + 18f, y + 64f, 7f, Color.rgb(198, 213, 201)
            )
            y += 76f
        }

        private fun drawProfileAndOverview() {
            val profile = data.profile
            val age = profile?.birthYear?.let { LocalDate.now().year - it }?.takeIf { it in 0..120 }
            val conditions = profile?.conditions
                ?.split(',')?.filter { it.isNotBlank() }
                ?.mapNotNull { key -> SymptomCatalog.conditions.firstOrNull { it.key == key }?.label }
                ?.map { translate(it, language) }
                .orEmpty()

            val c = canvas ?: return
            c.drawRoundRect(RectF(margin, y, contentRight, y + 50f), 8f, 8f, fill(cream))
            drawText(if (english) "Name" else "称呼", margin + 12f, y + 14f, 6.8f, muted)
            drawText(
                profile?.displayName?.ifBlank { if (english) "Not set" else "未填写" }
                    ?: if (english) "Not set" else "未填写",
                margin + 12f, y + 26f, 9.2f, ink, bold = true
            )
            drawText(if (english) "Age" else "年龄", margin + 160f, y + 14f, 6.8f, muted)
            drawText(
                age?.let { if (english) "$it" else "$it 岁" } ?: "—",
                margin + 160f, y + 26f, 8f, ink
            )
            drawText(if (english) "Gender" else "性别", margin + 220f, y + 14f, 6.8f, muted)
            drawText(
                profile?.gender?.ifBlank { "—" } ?: "—",
                margin + 220f, y + 26f, 8f, ink
            )
            drawText(if (english) "Focus" else "关注方向", margin + 12f, y + 42f, 6.8f, muted)
            val focus = conditions.joinToString(if (english) ", " else "、").ifBlank {
                if (english) "Not set" else "未填写"
            }
            val focusPaint = paint(7.6f, ink)
            c.drawText(
                ellipsize(focus, focusPaint, contentRight - margin - 96f),
                margin + 70f, y + 42f, focusPaint
            )
            y += 58f

            val days = daysBetween(data.startDate, data.endDate)
            val cards = listOf(
                Triple(if (english) "Days" else "记录天数", "${data.recordedDays} / $days", accentSoft),
                Triple(
                    if (english) "Mood" else "平均情绪",
                    data.averageMood?.let { String.format(Locale.US, "%.1f / 5", it) }
                        ?: (if (english) "—" else "暂无"),
                    claySoft
                ),
                Triple(
                    if (english) "Sleep" else "平均睡眠",
                    data.averageSleep?.let { String.format(Locale.US, "%.1fh", it) }
                        ?: (if (english) "—" else "暂无"),
                    blueSoft
                )
            )
            val gap = 7f
            val cardWidth = (contentWidth - gap * 2) / 3f
            cards.forEachIndexed { index, (label, value, bg) ->
                val left = margin + index * (cardWidth + gap)
                c.drawRoundRect(RectF(left, y, left + cardWidth, y + 38f), 7f, 7f, fill(bg))
                c.drawText(label, left + 8f, y + 12f, paint(6.6f, muted))
                c.drawText(value, left + 8f, y + 30f, paint(12f, ink, bold = true))
            }
            y += 44f

            val adherence = if (data.adherence.total == 0) {
                if (english) "Adherence: no scheduled medication records"
                else "用药依从：暂无定时用药记录"
            } else if (english) {
                "Adherence ${data.adherence.percent}% · ${data.adherence.taken} taken / " +
                    "${data.adherence.missed} missed"
            } else {
                "用药依从 ${data.adherence.percent}% · 已服 ${data.adherence.taken} / " +
                    "漏服 ${data.adherence.missed}"
            }
            drawText(adherence, margin, y + 4f, 7.2f, muted)
            y += 12f
        }

        private fun drawDiagnoses() {
            if (data.diagnoses.isEmpty()) return
            section(if (english) "Diagnoses" else "诊断记录")
            val items = data.diagnoses.take(3)
            items.forEach { diagnosis ->
                val name = diagnosis.displayName(english)
                val status = DiagnosisCatalog.statusLabel(diagnosis.status, english)
                val code = diagnosis.code.takeIf { it.isNotBlank() }
                val meta = buildString {
                    append(DiagnosisCatalog.standardLabel(diagnosis.standard, english))
                    diagnosis.diagnosedDate?.let { append(" · ${DateUtils.displayDate(it)}") }
                    if (diagnosis.clinician.isNotBlank()) {
                        append(" · ")
                        append(diagnosis.clinician)
                    }
                }
                val head = buildString {
                    if (code != null) append("$code  ")
                    append(name)
                    append("  ·  $meta")
                }
                val hp = paint(7.4f, ink)
                val sp = paint(6.4f, accent)
                val maxWidth = contentRight - margin - sp.measureText(status) - 12f
                canvas?.drawText(ellipsize(head, hp, maxWidth), margin, y + 7f, hp)
                canvas?.drawText(status, contentRight - sp.measureText(status), y + 7f, sp)
                y += 11f
            }
            if (data.diagnoses.size > items.size) {
                drawText(
                    if (english) "${data.diagnoses.size - items.size} more diagnoses are saved in the app."
                    else "另有 ${data.diagnoses.size - items.size} 条诊断记录保存在应用中。",
                    margin, y + 5f, 6.2f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawClinicalSummaryPoints() {
            ensureSpace(56f)
            section(if (english) "Summary" else "摘要")
            val days = daysBetween(data.startDate, data.endDate)
            val bullets = mutableListOf<String>()
            val missing = (days - data.recordedDays).coerceAtLeast(0)
            bullets += if (english) {
                "Recording completeness: ${data.recordedDays} / $days days; $missing missing."
            } else {
                "记录完整性：${data.recordedDays} / $days 天；$missing 天未记录。"
            }
            val rated = data.symptomStats.filter { it.averageSeverity != null }
            if (rated.isNotEmpty()) {
                val top = rated.take(3).joinToString(if (english) ", " else "、") {
                    "${translate(it.name, language)} ${it.days}${if (english) "d" else "天"}"
                }
                bullets += if (english) {
                    "${rated.size} symptoms have intensity ratings; most frequently recorded: $top."
                } else {
                    "有强度评分的症状 ${rated.size} 项；记录最多：$top。"
                }
            }
            if (data.adherence.total > 0) {
                bullets += if (english) {
                    "Medication adherence: ${data.adherence.percent}% (${data.adherence.taken} taken / ${data.adherence.missed} missed)."
                } else {
                    "用药依从：${data.adherence.percent}%（已服 ${data.adherence.taken} / 漏服 ${data.adherence.missed}）。"
                }
            }
            val maxIdeation = data.checkIns.mapNotNull { it.suicidalIdeation }.maxOrNull() ?: 0
            if (maxIdeation > 0) {
                val label = SafetyCatalog.levels.firstOrNull { it.value == maxIdeation }?.label ?: maxIdeation.toString()
                bullets += if (english) {
                    "Safety: ${data.ideationEvents.size} day(s) with self-harm thoughts; highest level: ${translate(label, language)}."
                } else {
                    "安全记录：${data.ideationEvents.size} 天记录伤害自己的念头；最高等级：${translate(label, language)}。"
                }
            } else {
                bullets += if (english) {
                    "Safety: no self-harm thoughts were recorded in this period."
                } else {
                    "安全记录：这段时间没有记录伤害自己的念头。"
                }
            }
            bullets.forEach { bullet ->
                drawText("• $bullet", margin, y + 8f, 8.2f, ink)
                y += 12f
            }
            y += 2f
        }

        private fun drawDiagnosesDetailed() {
            if (data.diagnoses.isEmpty()) return
            ensureSpace(54f)
            section(if (english) "Diagnoses" else "诊断记录")
            val items = data.diagnoses.take(3)
            items.forEach { diagnosis ->
                val name = diagnosis.displayName(english)
                val status = DiagnosisCatalog.statusLabel(diagnosis.status, english)
                val code = diagnosis.code.takeIf { it.isNotBlank() }
                val head = buildString {
                    if (code != null) append("$code  ")
                    append(name)
                }
                drawText(head, margin, y + 9f, 9.4f, ink, bold = true)
                val statusPaint = paint(7.4f, accent)
                canvas?.drawText(
                    status, contentRight - statusPaint.measureText(status), y + 9f, statusPaint
                )
                y += 13f
                val meta = buildString {
                    append(DiagnosisCatalog.standardLabel(diagnosis.standard, english))
                    diagnosis.diagnosedDate?.let { append(" · ${DateUtils.displayDate(it)}") }
                    if (diagnosis.clinician.isNotBlank()) {
                        append(" · ")
                        append(diagnosis.clinician)
                    }
                }
                drawText(meta, margin + 4f, y + 7f, 7.6f, muted)
                y += 12f
            }
            if (data.diagnoses.size > items.size) {
                drawText(
                    if (english) "${data.diagnoses.size - items.size} more diagnoses are saved in the app."
                    else "另有 ${data.diagnoses.size - items.size} 条诊断记录保存在应用中。",
                    margin, y + 5f, 7f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawMedicationSummary() {
            ensureSpace(56f)
            section(if (english) "Current medication" else "目前用药")
            if (data.medications.isEmpty()) {
                drawText(
                    if (english) "No medication records." else "这段时间没有药物记录。",
                    margin, y + 8f, 8.2f, muted
                )
                y += 15f
                return
            }
            data.medications.take(3).forEach { med ->
                val line = buildString {
                    append("• ")
                    append(med.displayLabel())
                    append(" · ")
                    append(medicationSchedule(med))
                    if (!med.active) append(if (english) " (stopped)" else "（已停用）")
                }
                val paint = paint(8.2f, ink)
                canvas?.drawText(ellipsize(line, paint, contentWidth), margin, y + 8f, paint)
                y += 12f
            }
            if (data.medications.size > 3) {
                drawText(
                    if (english) "${data.medications.size - 3} more medications in the app."
                    else "另有 ${data.medications.size - 3} 种药物记录在应用中。",
                    margin, y + 5f, 7f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawSymptomOverview() {
            ensureSpace(60f)
            section(if (english) "Symptom overview" else "症状概览")
            val stats = data.symptomStats
            if (stats.isEmpty()) {
                drawText(
                    if (english) "No symptoms were recorded." else "这段时间没有勾选症状记录。",
                    margin, y + 8f, 8.2f, muted
                )
                y += 15f
                return
            }

            val nameX = margin
            val daysX = margin + 230f
            val averageX = margin + 320f
            val maxX = margin + 402f
            canvas?.drawRoundRect(
                RectF(margin, y, contentRight, y + 16f), 3f, 3f, fill(accentSoft)
            )
            drawText(if (english) "Symptom" else "症状", nameX + 5f, y + 11f, 7.8f, ink, bold = true)
            drawText(if (english) "Days" else "天数", daysX, y + 11f, 7.8f, ink, bold = true)
            drawText(if (english) "Avg" else "平均", averageX, y + 11f, 7.8f, ink, bold = true)
            drawText(if (english) "Max" else "最高", maxX, y + 11f, 7.8f, ink, bold = true)
            y += 17f

            val items = stats.take(20)
            items.forEach { stat ->
                val name = translate(stat.name, language)
                val namePaint = paint(8f, ink)
                canvas?.drawText(ellipsize(name, namePaint, 218f), nameX, y + 9f, namePaint)
                drawText(stat.days.toString(), daysX, y + 9f, 8f, ink)
                drawText(
                    stat.averageSeverity?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    averageX, y + 9f, 8f, ink
                )
                drawText(stat.maxSeverity?.toString() ?: "—", maxX, y + 9f, 8f, ink)
                canvas?.drawLine(margin, y + 12.5f, contentRight, y + 12.5f, line(0.4f, lineColor))
                y += 13.5f
            }
            if (stats.size > items.size) {
                drawText(
                    if (english) "${stats.size - items.size} more symptoms in the app."
                    else "另有 ${stats.size - items.size} 项症状记录在应用中。",
                    margin, y + 5f, 7f, muted
                )
                y += 8f
            }
            drawText(
                if (english) {
                    "Intensity averages use recorded 0–10 ratings only. Dashed lines are drawn for symptoms with ratings."
                } else {
                    "平均强度只计算填写过的 0–10 评分；仅对有评分的症状绘制折线图。"
                },
                margin, y + 6f, 7f, muted
            )
            y += 10f
            drawText(
                if (english) {
                    "Blank = no check-in. 0 = check-in exists but the symptom was not selected."
                } else {
                    "空白 = 未记录；0 = 当天有记录，但没有勾选该症状。"
                },
                margin, y + 6f, 7f, muted
            )
            y += 11f
        }

        private fun drawMedicationDetails() {
            ensureSpace(58f)
            section(if (english) "Medication details" else "用药明细")
            if (data.medications.isEmpty()) {
                drawText(
                    if (english) "No medication records." else "这段时间没有药物记录。",
                    margin, y + 8f, 8.2f, muted
                )
                y += 15f
                return
            }
            val items = data.medications.take(6)
            items.forEach { med ->
                val heading = buildString {
                    append("• ")
                    append(med.displayLabel())
                    if (!med.active) append(if (english) " (stopped)" else "（已停用）")
                }
                drawText(heading, margin, y + 8f, 8.4f, ink, bold = true)
                y += 12f
                val prnCount = if (med.asNeeded) {
                    data.medicationLogs.count { it.medicationId == med.id && it.status == "taken" }
                } else {
                    0
                }
                val details = buildString {
                    append(medicationSchedule(med))
                    append(" · ")
                    append(med.startDate)
                    med.endDate?.let { append(" → $it") }
                    if (med.asNeeded) {
                        append(if (english) " · $prnCount logged" else " · 已记录 $prnCount 次")
                    }
                }
                drawText(details, margin + 5f, y + 7f, 7.4f, muted)
                y += 13f
            }
            if (data.medications.size > items.size) {
                drawText(
                    if (english) "${data.medications.size - items.size} more medications in the app."
                    else "另有 ${data.medications.size - items.size} 种药物记录在应用中。",
                    margin, y + 5f, 7f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawScalesDetailed() {
            ensureSpace(58f)
            section(if (english) "Self-assessments" else "自评量表")
            if (data.scales.isEmpty()) {
                drawText(
                    if (english) "No assessments in this period." else "这段时间没有完成自评量表。",
                    margin, y + 8f, 8.2f, muted
                )
                y += 15f
                return
            }
            val items = data.scales.sortedByDescending { it.createdAt }.take(10)
            items.forEach { result ->
                val def = ScaleCatalog.byType(result.type)
                val name = def?.shortName ?: result.type
                val band = def?.let { d ->
                    d.band(result.severity)?.let { b -> d.bandLabel(b, english) }
                } ?: result.severity
                val date = DateUtils.iso(
                    DateUtils.toLocalDateTime(result.createdAt).toLocalDate()
                )
                val line = "$date · $name  ${result.score} / ${def?.maxScore ?: "?"} · $band"
                drawText(line, margin, y + 8f, 8f, ink)
                y += 13f
            }
            if (data.scales.size > items.size) {
                drawText(
                    if (english) "${data.scales.size - items.size} more assessments in the app."
                    else "另有 ${data.scales.size - items.size} 次测评记录在应用中。",
                    margin, y + 5f, 7f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawClinicalDataNotes() {
            ensureSpace(48f)
            section(if (english) "How to read this report" else "报告说明")
            val notes = if (english) {
                listOf(
                    "The report only summarizes what the user entered; a blank day does not mean a symptom was absent.",
                    "Symptom intensity is a user-rated 0–10 score, not a diagnostic scale.",
                    "Adherence is calculated from scheduled medication records marked taken or missed."
                )
            } else {
                listOf(
                    "报告只汇总使用者自填的内容；空白日期不等于没有症状。",
                    "症状强度是使用者自评的 0–10 分，不是诊断量表。",
                    "用药依从率按定时药物记录中的已服与漏服计算。"
                )
            }
            notes.forEach { note ->
                drawText("• $note", margin, y + 7f, 7.6f, muted)
                y += 11f
            }
            y += 2f
        }

        private fun drawClinicalSymptomCharts(stats: List<SymptomStat>) {
            newPage(first = false)
            var index = 0
            var firstPage = true
            while (index < stats.size) {
                if (!firstPage) {
                    finishPage()
                    newPage(first = false)
                }
                section(
                    if (firstPage) {
                        if (english) "Symptom intensity over time" else "症状强度变化（0–10）"
                    } else {
                        if (english) "Symptom intensity continued" else "症状强度变化（续）"
                    }
                )
                if (firstPage) {
                    drawText(
                        if (english) {
                            "Solid line: daily intensity. Dashed line: average of recorded ratings. Gaps: no check-in."
                        } else {
                            "实线为每日强度，虚线为已记录评分的平均强度；空白表示当天没有记录。"
                        },
                        margin, y + 6f, 7.2f, muted
                    )
                    y += 10f
                }
                val end = minOf(index + CLINICAL_CHARTS_PER_PAGE, stats.size)
                y = drawClinicalSymptomGrid(stats.subList(index, end))
                index = end
                firstPage = false
            }
            drawDisclaimer()
            finishPage()
        }

        private fun drawClinicalSymptomGrid(stats: List<SymptomStat>): Float {
            val dates = datesBetween(data.startDate, data.endDate)
            val byDate = data.checkIns.associateBy { it.date }
            val labels = dates.map { DateUtils.displayShort(DateUtils.iso(it)) }
            val gap = 14f
            val chartWidth = (contentWidth - gap) / CLINICAL_SYMPTOM_COLUMNS
            val chartHeight = 148f
            val rowPitch = 172f
            stats.forEachIndexed { index, stat ->
                val column = index % CLINICAL_SYMPTOM_COLUMNS
                val row = index / CLINICAL_SYMPTOM_COLUMNS
                val left = margin + column * (chartWidth + gap)
                val top = y + row * rowPitch
                val values = dates.map { date ->
                    val entry = byDate[DateUtils.iso(date)] ?: return@map null
                    val severity = parseSymptomSeverity(entry.symptomSeverity)
                    val level = severity[stat.id]
                    when {
                        level != null -> level.toFloat()
                        entry.symptoms.split(',').contains(stat.id) -> null
                        else -> 0f
                    }
                }
                val name = translate(stat.name, language)
                val count = if (english) "${stat.days}d" else "${stat.days}天"
                drawMiniChart(
                    "$name · $count",
                    labels, values, 0f, 10f, clay,
                    left, top, chartWidth, chartHeight,
                    average = stat.averageSeverity,
                    large = true
                )
            }
            val rows = (stats.size + CLINICAL_SYMPTOM_COLUMNS - 1) / CLINICAL_SYMPTOM_COLUMNS
            return y + rows * rowPitch
        }

        private fun medicationSchedule(med: com.mindtrace.app.data.MedicationEntity): String {
            if (med.asNeeded) return if (english) "PRN" else "按需"
            val times = med.times.split(',').filter { it.isNotBlank() }
                .joinToString(if (english) ", " else "、")
            return times.ifBlank { if (english) "No set time" else "未设时间" }
        }

        private fun drawTrends() {
            section(if (english) "Trends" else "趋势")
            val dates = datesBetween(data.startDate, data.endDate)
            val byDate = data.checkIns.associateBy { it.date }
            val labels = dates.map { DateUtils.displayShort(DateUtils.iso(it)) }
            val moods = dates.map { date -> byDate[DateUtils.iso(date)]?.mood?.toFloat() }
            val anxieties = dates.map { date -> byDate[DateUtils.iso(date)]?.anxiety?.toFloat() }
            val sleeps = dates.map { date -> byDate[DateUtils.iso(date)]?.sleepHours }

            val gap = 8f
            val chartWidth = (contentWidth - gap * 2) / 3f
            val top = y
            drawMiniChart(
                if (english) "Mood 1–5" else "情绪 1–5",
                labels, moods, 1f, 5f, accent,
                margin, top, chartWidth, 66f
            )
            drawMiniChart(
                if (english) "Anxiety 0–10" else "焦虑 0–10",
                labels, anxieties, 0f, 10f, clay,
                margin + chartWidth + gap, top, chartWidth, 66f
            )
            drawMiniChart(
                if (english) "Sleep h" else "睡眠 小时",
                labels, sleeps, 0f, 12f, blue,
                margin + (chartWidth + gap) * 2, top, chartWidth, 66f
            )
            y = top + 74f
        }

        /**
         * One 0–10 intensity chart per symptom. Up to 11 charts fit below the
         * three overview charts; any extra charts continue on a new page.
         */
        private fun drawSymptomCharts() {
            val stats = data.symptomStats.filter { it.averageSeverity != null }
            if (stats.isEmpty()) return

            val dates = datesBetween(data.startDate, data.endDate)
            val byDate = data.checkIns.associateBy { it.date }
            val labels = dates.map { DateUtils.displayShort(DateUtils.iso(it)) }

            section(if (english) "Symptom intensity 0–10" else "症状强度 0–10")
            drawText(
                if (english) {
                    "Solid line: daily intensity. Dashed line: average of recorded ratings. Gaps: no record."
                } else {
                    "实线为每日强度，虚线为该症状已记录评分的平均值；空白表示当天没有记录。"
                },
                margin, y + 5f, 6.1f, muted
            )
            y += 9f

            val first = stats.take(FIRST_PAGE_SYMPTOM_CHARTS)
            y = drawSymptomGrid(first, dates, byDate, labels, y)

            var remaining = stats.drop(FIRST_PAGE_SYMPTOM_CHARTS)
            while (remaining.isNotEmpty()) {
                finishPage()
                newPage(first = false)
                section(
                    if (english) "Symptom intensity continued" else "症状强度（续）"
                )
                val batch = remaining.take(SYMPTOM_CHARTS_PER_CONTINUATION_PAGE)
                y = drawSymptomGrid(batch, dates, byDate, labels, y)
                remaining = remaining.drop(SYMPTOM_CHARTS_PER_CONTINUATION_PAGE)
            }
        }

        private fun drawSymptomGrid(
            stats: List<SymptomStat>,
            dates: List<LocalDate>,
            byDate: Map<String, CheckInEntity>,
            labels: List<String>,
            startY: Float
        ): Float {
            val gap = 8f
            val chartWidth = (contentWidth - gap * (SYMPTOM_COLUMNS - 1)) / SYMPTOM_COLUMNS
            val chartHeight = 45f
            val rowPitch = 55f
            stats.forEachIndexed { index, stat ->
                val column = index % SYMPTOM_COLUMNS
                val row = index / SYMPTOM_COLUMNS
                val left = margin + column * (chartWidth + gap)
                val top = startY + row * rowPitch
                val values = dates.map { date ->
                    val entry = byDate[DateUtils.iso(date)] ?: return@map null
                    val severity = parseSymptomSeverity(entry.symptomSeverity)
                    val level = severity[stat.id]
                    when {
                        level != null -> level.toFloat()
                        entry.symptoms.split(',').contains(stat.id) -> null
                        else -> 0f
                    }
                }
                val name = translate(stat.name, language)
                val count = if (english) "${stat.days}d" else "${stat.days}天"
                drawMiniChart(
                    "$name · $count",
                    labels, values, 0f, 10f, clay,
                    left, top, chartWidth, chartHeight,
                    average = stat.averageSeverity
                )
            }
            val rows = (stats.size + SYMPTOM_COLUMNS - 1) / SYMPTOM_COLUMNS
            return startY + rows * rowPitch
        }

        private fun drawMiniChart(
            title: String,
            labels: List<String>,
            values: List<Float?>,
            min: Float,
            max: Float,
            color: Int,
            left: Float,
            top: Float,
            chartWidth: Float,
            chartHeight: Float,
            average: Float? = null,
            large: Boolean = false
        ) {
            val c = canvas ?: return
            val titleSize = if (large) 9f else 7.2f
            val titlePaint = paint(titleSize, ink, bold = true)
            c.drawText(ellipsize(title, titlePaint, chartWidth - 2f), left, top + 7f, titlePaint)
            val areaTop = top + if (large) 14f else 12f
            val areaBottom = top + chartHeight
            val areaLeft = left
            val areaRight = left + chartWidth
            c.drawRoundRect(
                RectF(areaLeft, areaTop, areaRight, areaBottom),
                4f, 4f, fill(Color.rgb(250, 249, 246))
            )
            repeat(3) { index ->
                val gy = areaTop + (areaBottom - areaTop) * index / 2f
                c.drawLine(areaLeft, gy, areaRight, gy, line(0.6f, lineColor))
            }
            val axis = paint(if (large) 7f else 5.8f, muted)
            c.drawText(formatValue(max), left, areaTop + if (large) 6f else 4f, axis)
            c.drawText(formatValue(min), left, areaBottom, axis)
            val plotLeft = areaLeft + if (large) 20f else 15f
            val plotRight = areaRight - 3f
            val plotTop = areaTop + if (large) 4f else 3f
            val plotBottom = areaBottom - if (large) 11f else 8f
            if (values.any { it != null }) {
                val step = if (values.size <= 1) 0f else (plotRight - plotLeft) / (values.size - 1)
                fun x(index: Int) = plotLeft + step * index
                fun yFor(value: Float): Float {
                    val ratio = ((value - min) / (max - min).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    return plotBottom - (plotBottom - plotTop) * ratio
                }
                var segment = mutableListOf<Pair<Int, Float>>()
                fun flushArea() {
                    if (segment.size < 2) { segment = mutableListOf(); return }
                    val path = Path().apply {
                        moveTo(x(segment.first().first), plotBottom)
                        segment.forEach { (i, v) -> lineTo(x(i), yFor(v)) }
                        lineTo(x(segment.last().first), plotBottom)
                        close()
                    }
                    val areaPaint = paint(0f, color).apply {
                        style = Paint.Style.FILL
                        shader = LinearGradient(
                            0f, plotTop, 0f, plotBottom,
                            Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(4, Color.red(color), Color.green(color), Color.blue(color)),
                            Shader.TileMode.CLAMP
                        )
                    }
                    c.drawPath(path, areaPaint)
                    segment = mutableListOf()
                }
                values.forEachIndexed { index, value ->
                    if (value == null) flushArea() else segment.add(index to value)
                }
                flushArea()

                val dataStroke = stroke(color).apply {
                    strokeWidth = if (large) 1.9f else 1.4f
                }
                var path: Path? = null
                values.forEachIndexed { index, value ->
                    if (value == null) {
                        path?.let { c.drawPath(it, dataStroke) }
                        path = null
                    } else {
                        if (path == null) path = Path().apply { moveTo(x(index), yFor(value)) }
                        else path?.lineTo(x(index), yFor(value))
                    }
                }
                path?.let { c.drawPath(it, dataStroke) }
                values.forEachIndexed { index, value ->
                    if (value != null) {
                        c.drawCircle(
                            x(index), yFor(value),
                            if (large) 2.4f else 1.7f,
                            fill(color)
                        )
                    }
                }
                if (average != null) {
                    val averageY = yFor(average)
                    val averageLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        style = Paint.Style.STROKE
                        strokeWidth = if (large) 1.1f else 0.8f
                        alpha = 150
                        pathEffect = DashPathEffect(
                            if (large) floatArrayOf(4f, 3f) else floatArrayOf(2.6f, 2.6f),
                            0f
                        )
                    }
                    c.drawLine(plotLeft, averageY, plotRight, averageY, averageLine)
                    val averageLabel = (if (english) "avg " else "均 ") +
                        String.format(Locale.US, "%.1f", average)
                    val labelPaint = paint(if (large) 7f else 5.1f, color)
                    val labelWidth = labelPaint.measureText(averageLabel)
                    val labelX = plotRight - labelWidth - 1.5f
                    val labelY = (averageY - if (large) 3.4f else 2.4f)
                        .coerceIn(plotTop + if (large) 7f else 5f, plotBottom)
                    c.drawRect(
                        labelX - 2f,
                        labelY - if (large) 7.5f else 5.2f,
                        labelX + labelWidth + 2f,
                        labelY + 1.5f,
                        fill(Color.argb(226, 255, 255, 255))
                    )
                    c.drawText(averageLabel, labelX, labelY, labelPaint)
                }
                if (labels.isNotEmpty()) {
                    val lp = paint(if (large) 6.8f else 5.5f, muted)
                    val labelBaseline = areaBottom + if (large) 9f else 6f
                    c.drawText(labels.first(), plotLeft, labelBaseline, lp)
                    val last = labels.last()
                    c.drawText(
                        last,
                        plotRight - lp.measureText(last),
                        labelBaseline,
                        lp
                    )
                }
            } else {
                c.drawText(
                    if (english) "no data" else "暂无",
                    plotLeft + 8f, (plotTop + plotBottom) / 2f, paint(6.2f, muted)
                )
            }
        }

        private fun drawMedicationsAndScales() {
            ensureSpace(82f)
            section(if (english) "Medication & self-assessments" else "用药与自评量表")
            val gap = 14f
            val columnWidth = (contentWidth - gap) / 2f
            val columnTop = y
            drawText(
                if (english) "Medication" else "用药",
                margin, columnTop + 7f, 7.4f, accentDark, bold = true
            )
            drawText(
                if (english) "Self-assessments" else "自评量表",
                margin + columnWidth + gap, columnTop + 7f, 7.4f, accentDark, bold = true
            )
            var rowY = columnTop + 16f

            val meds = data.medications.take(3)
            val scales = data.scales.sortedByDescending { it.createdAt }.take(3)
            val rows = maxOf(meds.size, scales.size).coerceAtLeast(1)
            for (index in 0 until rows) {
                if (meds.isEmpty()) {
                    if (index == 0) {
                        drawText(
                            if (english) "No medication records." else "没有药物记录。",
                            margin, rowY + 6f, 6.2f, muted
                        )
                    }
                } else if (index < meds.size) {
                    val med = meds[index]
                    val schedule = if (med.asNeeded) {
                        if (english) "PRN" else "按需"
                    } else {
                        med.times.split(',').filter { it.isNotBlank() }
                            .joinToString(if (english) ", " else "、")
                    }
                    val line = buildString {
                        append("• ")
                        append(med.displayLabel())
                        if (schedule.isNotBlank()) append(" · ").append(schedule)
                        if (!med.active) append(if (english) " (stopped)" else "（已停用）")
                    }
                    val paint = paint(6.6f, ink)
                    canvas?.drawText(
                        ellipsize(line, paint, columnWidth),
                        margin, rowY + 7f, paint
                    )
                }

                if (scales.isEmpty()) {
                    if (index == 0) {
                        drawText(
                            if (english) "No assessments." else "没有自评记录。",
                            margin + columnWidth + gap, rowY + 6f, 6.2f, muted
                        )
                    }
                } else if (index < scales.size) {
                    val result = scales[index]
                    val def = ScaleCatalog.byType(result.type)
                    val name = def?.shortName ?: result.type
                    val band = def?.let { d ->
                        d.band(result.severity)?.let { b -> d.bandLabel(b, english) }
                    } ?: result.severity
                    val date = DateUtils.displayShort(
                        DateUtils.iso(DateUtils.toLocalDateTime(result.createdAt).toLocalDate())
                    )
                    val line = "$date · $name ${result.score}/${def?.maxScore ?: "?"} · $band"
                    val paint = paint(6.1f, ink)
                    canvas?.drawText(
                        ellipsize(line, paint, columnWidth),
                        margin + columnWidth + gap, rowY + 7f, paint
                    )
                }
                rowY += 10.5f
            }

            val moreMeds = data.medications.size - meds.size
            val moreScales = data.scales.size - scales.size
            if (moreMeds > 0) {
                drawText(
                    if (english) "$moreMeds more in app" else "另有 $moreMeds 种",
                    margin, rowY + 5f, 5.8f, muted
                )
            }
            if (moreScales > 0) {
                drawText(
                    if (english) "$moreScales more in app" else "另有 $moreScales 次",
                    margin + columnWidth + gap, rowY + 5f, 5.8f, muted
                )
            }
            if (moreMeds > 0 || moreScales > 0) rowY += 7f
            y = rowY + 2f
        }

        private fun drawSafety() {
            if (data.ideationEvents.isEmpty()) return
            ensureSpace(44f)
            section(if (english) "Safety-related records" else "安全相关记录")
            data.ideationEvents.take(2).forEach { (date, rawLabel) ->
                drawText(
                    "• ${DateUtils.displayDate(date)}：${translate(rawLabel, language)}",
                    margin, y + 7f, 7.2f, clay
                )
                y += 11f
            }
            if (data.ideationEvents.size > 2) {
                drawText(
                    if (english) "${data.ideationEvents.size - 2} more days in the app."
                    else "另有 ${data.ideationEvents.size - 2} 天记录在应用中。",
                    margin, y + 5f, 6.4f, muted
                )
                y += 8f
            }
            y += 2f
        }

        private fun drawQuestions() {
            if (data.options.doctorQuestions.isBlank()) return
            ensureSpace(42f)
            section(if (english) "Questions for my clinician" else "我想和医生讨论")
            paragraph(data.options.doctorQuestions, 7.6f, ink, 0f, maxLines = 3)
            y += 2f
        }

        private fun drawDisclaimer() {
            ensureSpace(58f)
            section(if (english) "Notes" else "说明")
            paragraph(
                if (english) {
                    "Generated by MindTrace from user-entered records. For self-observation and clinical communication only. " +
                        "Not a diagnosis, prescription or treatment recommendation. Data was not uploaded."
                } else {
                    "本报告由心迹根据使用者本人填写的记录自动汇总，仅用于自我观察和就诊沟通。" +
                        "不构成诊断、处方或治疗建议。数据未上传。"
                },
                6.8f, muted, 0f, maxLines = 3
            )
            val levels = SafetyCatalog.levels.joinToString(if (english) "; " else "；") {
                "${it.value}=${translate(it.label, language)}"
            }
            paragraph(
                (if (english) "Self-harm thought levels: " else "伤害自己念头的分级：") + levels + ".",
                6.3f, muted, 0f, maxLines = 2
            )
        }

        private fun drawJournals() {
            section(if (english) "Journal excerpts" else "日记摘录")
            val items = data.journals.take(6)
            items.forEach { entry ->
                val title = entry.title.ifBlank { if (english) "Untitled" else "无标题" }
                drawText(
                    "${DateUtils.displayDate(entry.date)} · $title",
                    margin, y + 8f, 8.4f, ink, bold = true
                )
                y += 13f
                val content = entry.content.replace('\n', ' ').take(220)
                paragraph(
                    content + if (entry.content.length > 220) "…" else "",
                    7.2f, muted, 5f, maxLines = 2
                )
                y += 3f
            }
            if (data.journals.size > items.size) {
                drawText(
                    if (english) "${data.journals.size - items.size} more journal entries are saved in the app."
                    else "另有 ${data.journals.size - items.size} 篇日记保存在应用中。",
                    margin, y + 9f, 7f, muted
                )
                y += 16f
            }
            y += 8f
            paragraph(
                if (english) {
                    "Journal excerpts can contain highly sensitive content. Check who you share this PDF with."
                } else {
                    "日记摘录可能包含高度敏感的内容，分享 PDF 前请确认接收对象。"
                },
                6.8f, muted, 0f, maxLines = 3
            )
        }

        private fun ensureSpace(required: Float) {
            if (y + required > height - 36f) {
                finishPage()
                newPage(first = false)
            }
        }

        private fun section(title: String) {
            val c = canvas ?: return
            y += 4f
            c.drawRoundRect(RectF(margin, y, margin + 3f, y + 11f), 1.5f, 1.5f, fill(accent))
            c.drawText(title, margin + 9f, y + 9f, paint(10f, ink, bold = true))
            y += 13f
            c.drawRect(margin, y, contentRight, y + 0.7f, fill(lineColor))
            y += 7f
        }

        private fun paragraph(
            text: String,
            size: Float,
            color: Int,
            indent: Float,
            maxLines: Int
        ) {
            val p = paint(size, color)
            val lines = wrap(text, p, contentRight - margin - indent).take(maxLines)
            val lineHeight = size * 1.45f
            lines.forEach { line ->
                canvas?.drawText(line, margin + indent, y + size, p)
                y += lineHeight
            }
            y += 2f
        }

        private fun drawText(
            text: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) {
            canvas?.drawText(text, x, baseline, paint(size, color, bold))
        }

        private fun paint(size: Float, color: Int, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

        private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        private fun line(width: Float, color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
        }

        private fun stroke(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return emptyList()
            val result = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    result.add("")
                    return@forEach
                }
                var line = StringBuilder()
                paragraph.split(' ').forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        line = StringBuilder(candidate)
                    } else {
                        if (line.isNotEmpty()) {
                            result.add(line.toString())
                            line = StringBuilder()
                        }
                        if (paint.measureText(word) <= maxWidth) {
                            line = StringBuilder(word)
                        } else {
                            var chunk = StringBuilder()
                            word.forEach { ch ->
                                if (chunk.isNotEmpty() && paint.measureText(chunk.toString() + ch) > maxWidth) {
                                    result.add(chunk.toString())
                                    chunk = StringBuilder()
                                }
                                chunk.append(ch)
                            }
                            line = chunk
                        }
                    }
                }
                if (line.isNotEmpty()) result.add(line.toString())
            }
            return result
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var value = text
            while (value.isNotEmpty() && paint.measureText("$value…") > maxWidth) {
                value = value.dropLast(1)
            }
            return "$value…"
        }

        private fun formatValue(value: Float): String =
            if (value == value.toInt().toFloat()) value.toInt().toString()
            else String.format(Locale.US, "%.1f", value)

        private fun datesBetween(start: String, end: String): List<LocalDate> {
            val startDate = DateUtils.parse(start) ?: return emptyList()
            val endDate = DateUtils.parse(end) ?: return emptyList()
            val result = mutableListOf<LocalDate>()
            var day = startDate
            while (!day.isAfter(endDate)) {
                result.add(day)
                day = day.plusDays(1)
            }
            return result
        }

        private fun daysBetween(start: String, end: String): Int =
            datesBetween(start, end).size
    }
}
