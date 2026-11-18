package com.mindtrace.app.data

import java.util.Locale

data class ScaleOption(
    val label: String,
    val labelEn: String,
    val value: Int
)

data class ScaleQuestion(
    val text: String,
    val textEn: String,
    val options: List<ScaleOption>
)

data class SeverityBand(
    val min: Int,
    val max: Int,
    val key: String,
    val label: String,
    val labelEn: String,
    val advice: String,
    val adviceEn: String
)

enum class ScaleScoring {
    SUM,
    MDQ,
    PSQI,
    DASS21,

    /** 取所有题目的平均值（四舍五入），用于 DES-II 这类按平均分解释的量表。 */
    MEAN,

    /** ASRS-18：A/B 两部分各 9 题，分别求和 0-36，取较高者作为总分。 */
    ASRS18,

    /** BSL-23：23 个计分题的平均分，按 0.1 精度保存（0-40 表示 0.0-4.0）。 */
    BSL23,

    /** C-SSRS 筛查版：统计 6 个是/否题中的阳性数量，并结合条目分级。 */
    CSSRS
}

data class ScaleDef(
    val type: String,
    val name: String,
    val nameEn: String,
    val shortName: String,
    val category: String,
    val categoryEn: String,
    val description: String,
    val descriptionEn: String,
    val timeframe: String,
    val timeframeEn: String,
    val instruction: String,
    val instructionEn: String,
    val questions: List<ScaleQuestion>,
    val bands: List<SeverityBand>,
    val scoring: ScaleScoring = ScaleScoring.SUM,
    val maxScoreOverride: Int? = null,
    val estimatedMinutes: Int = 2,
    val featured: Boolean = false,
    val source: String = "",
    val sourceEn: String = "",
    val note: String = "",
    val noteEn: String = "",
    val selfHarmQuestionIndex: Int? = null,
    /** 额外的自伤/自杀风险题索引（与 selfHarmQuestionIndex 一起触发安全提示）。 */
    val riskQuestionIndices: List<Int> = emptyList(),
    /** 参与计分的题目数量；补充题或不计分题放在末尾。 */
    val scoredQuestionCount: Int = questions.size,
    /** 不计分的补充题起始索引；用于在答题时显示说明。 */
    val supplementStartIndex: Int? = null,
    /** 计分结果的显示除数：1 表示整数分，10 表示 0.1 精度的平均分。 */
    val scoreDivisor: Int = 1
) {
    val maxScore: Int
        get() = maxScoreOverride
            ?: questions.take(scoredQuestionCount).sumOf { q -> q.options.maxOfOrNull { it.value } ?: 0 }

    val effectiveRiskQuestionIndices: List<Int>
        get() = listOfNotNull(selfHarmQuestionIndex) + riskQuestionIndices

    fun formatScore(raw: Int): String =
        if (scoreDivisor <= 1) raw.toString()
        else String.format(Locale.US, "%.1f", raw.toDouble() / scoreDivisor)

    fun formatMaxScore(): String = formatScore(maxScore)

    fun questionText(index: Int, english: Boolean): String {
        val question = questions.getOrNull(index) ?: return ""
        return if (english) question.textEn else question.text
    }

    fun optionLabel(questionIndex: Int, optionIndex: Int, english: Boolean): String {
        val option = questions.getOrNull(questionIndex)?.options?.getOrNull(optionIndex) ?: return ""
        return if (english) option.labelEn else option.label
    }

    private fun optionValue(questionIndex: Int, answerIndex: Int): Int {
        val option = questions.getOrNull(questionIndex)?.options?.getOrNull(answerIndex) ?: return 0
        return option.value
    }

    fun score(answers: List<Int>): Int = when (scoring) {
        ScaleScoring.MDQ -> (0 until 13).sumOf { optionValue(it, answers.getOrElse(it) { 1 }) }
        ScaleScoring.PSQI -> psqiScore(answers)
        ScaleScoring.ASRS18 -> {
            val inattention = (0..8).sumOf { index ->
                optionValue(index, answers.getOrElse(index) { 0 })
            }
            val hyperactivity = (9..17).sumOf { index ->
                optionValue(index, answers.getOrElse(index) { 0 })
            }
            maxOf(inattention, hyperactivity)
        }
        ScaleScoring.MEAN -> {
            val sum = (0 until minOf(scoredQuestionCount, answers.size)).sumOf { index ->
                optionValue(index, answers.getOrElse(index) { 0 })
            }
            if (scoredQuestionCount <= 0) 0 else (sum + scoredQuestionCount / 2) / scoredQuestionCount
        }
        ScaleScoring.BSL23 -> {
            val sum = (0 until minOf(scoredQuestionCount, 23)).sumOf { index ->
                optionValue(index, answers.getOrElse(index) { 0 })
            }
            if (sum == 0) 0 else (sum * 10 + 11) / 23
        }
        ScaleScoring.CSSRS -> (0 until minOf(6, answers.size)).count { index ->
            optionValue(index, answers.getOrElse(index) { 1 }) > 0
        }
        else -> (0 until minOf(scoredQuestionCount, answers.size)).sumOf { index ->
            optionValue(index, answers.getOrElse(index) { 0 })
        }
    }

    fun severity(answers: List<Int>): SeverityBand = when (scoring) {
        ScaleScoring.MDQ -> {
            val positiveCount = score(answers)
            val samePeriod = answers.getOrNull(13) == 0
            val impairment = answers.getOrNull(14) ?: 0
            val key = when {
                positiveCount >= 7 && samePeriod && impairment >= 2 -> "positive"
                positiveCount >= 7 -> "partial"
                else -> "negative"
            }
            bands.firstOrNull { it.key == key } ?: bands.last()
        }
        ScaleScoring.DASS21 -> severityDass21(answers)
        ScaleScoring.ASRS18 -> {
            val inattention = (0..8).sumOf { index -> optionValue(index, answers.getOrElse(index) { 0 }) }
            val hyperactivity = (9..17).sumOf { index -> optionValue(index, answers.getOrElse(index) { 0 }) }
            val key = when {
                inattention >= 17 && hyperactivity >= 17 -> "adhd_c"
                inattention >= 17 -> "adhd_i"
                hyperactivity >= 17 -> "adhd_h"
                else -> "unlikely"
            }
            bands.firstOrNull { it.key == key } ?: bands.last()
        }
        ScaleScoring.CSSRS -> {
            val positive = { index: Int ->
                optionValue(index, answers.getOrElse(index) { 1 }) > 0
            }
            val key = when {
                listOf(3, 4, 6).any(positive) -> "high"
                (0..2).any(positive) || positive(5) -> "moderate"
                else -> "negative"
            }
            bands.firstOrNull { it.key == key } ?: bands.last()
        }
        else -> severity(score(answers))
    }

    fun severity(score: Int): SeverityBand =
        bands.firstOrNull { score in it.min..it.max } ?: bands.last()

    fun band(key: String): SeverityBand? = bands.firstOrNull { it.key == key }

    fun nameFor(english: Boolean): String = if (english) nameEn else name
    fun categoryFor(english: Boolean): String = if (english) categoryEn else category
    fun descriptionFor(english: Boolean): String = if (english) descriptionEn else description
    fun timeframeFor(english: Boolean): String = if (english) timeframeEn else timeframe
    fun instructionFor(english: Boolean): String = if (english) instructionEn else instruction
    fun sourceFor(english: Boolean): String = if (english) sourceEn.ifBlank { source } else source
    fun noteFor(english: Boolean): String = if (english) noteEn.ifBlank { note } else note
    fun bandLabel(band: SeverityBand, english: Boolean): String =
        if (english) band.labelEn else band.label
    fun bandAdvice(band: SeverityBand, english: Boolean): String =
        if (english) band.adviceEn else band.advice

    fun detail(answers: List<Int>, english: Boolean): String? = when (scoring) {
        ScaleScoring.MDQ -> {
            val count = score(answers)
            val samePeriod = answers.getOrNull(13) == 0
            val impairment = answers.getOrNull(14) ?: 0
            val impairmentText = questions.getOrNull(14)?.options?.getOrNull(impairment)
            val impairmentLabel = impairmentText?.let { if (english) it.labelEn else it.label } ?: ""
            if (english) {
                "Positive symptoms: $count / 13 · Same period: ${if (samePeriod) "yes" else "no"} · Impairment: $impairmentLabel"
            } else {
                "阳性症状：$count / 13 · 是否同一时期：${if (samePeriod) "是" else "否"} · 影响程度：$impairmentLabel"
            }
        }
        ScaleScoring.DASS21 -> dassDetail(answers, english)
        ScaleScoring.PSQI -> psqiDetail(answers, english)
        else -> when (type) {
            "WHO5" -> {
                val percent = score(answers) * 4
                if (english) "Raw score ${score(answers)} / 25 · ${percent}%" else "原始分 ${score(answers)} / 25 · $percent%"
            }
            "WHODAS" -> {
                val percent = ((score(answers) * 100f) / 48f).toInt()
                if (english) "Total ${score(answers)} / 48 · ${percent}%" else "总分 ${score(answers)} / 48 · $percent%"
            }
            "ITQ" -> {
                val ptsd = (0..5).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
                val dso = (6..11).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
                val function = (12..17).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
                if (english) {
                    "PTSD symptoms $ptsd / 24 · Disturbances in self-organisation $dso / 24 · Functional impairment $function / 24"
                } else {
                    "PTSD 症状 $ptsd / 24 · 自我组织紊乱 $dso / 24 · 功能受损 $function / 24"
                }
            }
            "ASRS18" -> {
                val inattention = (0..8).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
                val hyperactivity = (9..17).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
                if (english) {
                    "Part A (inattention) $inattention / 36 · Part B (hyperactivity-impulsivity) $hyperactivity / 36 · interpretation uses the higher part"
                } else {
                    "A 部分（注意力不集中）$inattention / 36 · B 部分（冲动/多动）$hyperactivity / 36 · 分级取较高的一部分"
                }
            }
            "AUDITC" -> if (english) {
                "Cut-offs vary by sex: 3+ for women, 4+ for men. This is a screening result, not a diagnosis."
            } else {
                "不同性别的参考界值不同：女性 3 分及以上、男性 4 分及以上。此结果仅用于筛查，不是诊断。"
            }
            "BSL23" -> {
                val mean = String.format(Locale.US, "%.1f", score(answers).toDouble() / 10)
                val supplement = (scoredQuestionCount until questions.size).count { index ->
                    optionValue(index, answers.getOrElse(index) { 0 }) > 0
                }
                if (english) {
                    "Mean $mean / 4 · $supplement supplement behaviour item(s) reported at least once (not scored)"
                } else {
                    "平均分 $mean / 4 · 补充行为项目中有 $supplement 项报告至少一次（不计入总分）"
                }
            }
            "CSSRS" -> {
                val positives = (0 until minOf(scoredQuestionCount, 6)).filter { index ->
                    optionValue(index, answers.getOrElse(index) { 1 }) > 0
                }
                val list = if (positives.isEmpty()) {
                    if (english) "none" else "无"
                } else {
                    positives.joinToString("、") { "#${it + 1}" }
                }
                if (english) {
                    "Positive answers: $list · a yes on item 4, 5 or recent behaviour indicates high risk"
                } else {
                    "阳性条目：$list · 第 4、5 题或近三个月自杀行为为「是」属于高风险"
                }
            }
            "IESR" -> {
                val intrusion = subscale(answers, intArrayOf(0, 1, 2, 5, 8, 13, 15, 19))
                val avoidance = subscale(answers, intArrayOf(4, 6, 7, 10, 11, 12, 16, 21))
                val hyperarousal = subscale(answers, intArrayOf(3, 9, 14, 17, 18, 20))
                if (english) {
                    "Intrusion $intrusion / 32 · Avoidance $avoidance / 32 · Hyperarousal $hyperarousal / 24 · Total ${score(answers)} / 88"
                } else {
                    "侵入 $intrusion / 32 · 回避 $avoidance / 32 · 警觉 $hyperarousal / 24 · 总分 ${score(answers)} / 88"
                }
            }
            "MSPSS" -> {
                val significant = subscale(answers, intArrayOf(0, 1, 4, 9))
                val family = subscale(answers, intArrayOf(2, 3, 7, 10))
                val friends = subscale(answers, intArrayOf(5, 6, 8, 11))
                val mean = String.format(Locale.US, "%.1f", score(answers).toDouble() / 12)
                if (english) {
                    "Significant other $significant / 28 · Family $family / 28 · Friends $friends / 28 · Mean $mean / 7"
                } else {
                    "特别的人 $significant / 28 · 家人 $family / 28 · 朋友 $friends / 28 · 平均分 $mean / 7"
                }
            }
            "FFMQ39" -> {
                val observing = subscale(answers, intArrayOf(0, 5, 10, 14, 19, 25, 30, 35))
                val describing = subscale(answers, intArrayOf(1, 6, 11, 15, 21, 26, 31, 36))
                val awareness = subscale(answers, intArrayOf(4, 7, 12, 17, 22, 27, 33, 37))
                val nonjudging = subscale(answers, intArrayOf(2, 9, 13, 16, 24, 29, 34, 38))
                val nonreactivity = subscale(answers, intArrayOf(3, 8, 18, 20, 23, 28, 32))
                if (english) {
                    "Observing $observing / 40 · Describing $describing / 40 · Acting with awareness $awareness / 40 · " +
                        "Nonjudging $nonjudging / 40 · Nonreactivity $nonreactivity / 35"
                } else {
                    "观察 $observing / 40 · 描述 $describing / 40 · 觉知行动 $awareness / 40 · " +
                        "不评判 $nonjudging / 40 · 不反应 $nonreactivity / 35"
                }
            }
            "DERS36" -> {
                val nonacceptance = subscale(answers, intArrayOf(10, 11, 20, 22, 24, 28))
                val goals = subscale(answers, intArrayOf(12, 17, 19, 25, 32))
                val impulse = subscale(answers, intArrayOf(2, 13, 18, 23, 26, 31))
                val awareness = subscale(answers, intArrayOf(1, 5, 7, 9, 16, 33))
                val strategies = subscale(answers, intArrayOf(14, 15, 21, 27, 29, 30, 34, 35))
                val clarity = subscale(answers, intArrayOf(0, 3, 4, 6, 8))
                if (english) {
                    "Nonacceptance $nonacceptance / 30 · Goals $goals / 25 · Impulse $impulse / 30 · " +
                        "Awareness $awareness / 30 · Strategies $strategies / 40 · Clarity $clarity / 25"
                } else {
                    "不接受 $nonacceptance / 30 · 目标受阻 $goals / 25 · 冲动控制 $impulse / 30 · " +
                        "觉察 $awareness / 30 · 调节策略 $strategies / 40 · 清晰度 $clarity / 25"
                }
            }
            else -> null
        }
    }

    private fun severityDass21(answers: List<Int>): SeverityBand {
        val depression = subscale(answers, intArrayOf(2, 4, 9, 12, 15, 16, 20))
        val anxiety = subscale(answers, intArrayOf(1, 3, 6, 8, 14, 18, 19))
        val stress = subscale(answers, intArrayOf(0, 5, 7, 10, 11, 13, 17))
        val levels = listOf(
            dassLevel(depression, intArrayOf(4, 6, 10, 13)),
            dassLevel(anxiety, intArrayOf(3, 5, 7, 9)),
            dassLevel(stress, intArrayOf(7, 9, 12, 16))
        )
        val highest = levels.maxOrNull() ?: 0
        val key = when (highest) {
            0 -> "normal"
            1 -> "mild"
            2 -> "moderate"
            3 -> "severe"
            else -> "extremely_severe"
        }
        return bands.firstOrNull { it.key == key } ?: bands.last()
    }

    private fun dassLevel(value: Int, thresholds: IntArray): Int = when {
        value <= thresholds[0] -> 0
        value <= thresholds[1] -> 1
        value <= thresholds[2] -> 2
        value <= thresholds[3] -> 3
        else -> 4
    }

    private fun subscale(answers: List<Int>, indices: IntArray): Int =
        indices.sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }

    private fun dassDetail(answers: List<Int>, english: Boolean): String {
        val depression = subscale(answers, intArrayOf(2, 4, 9, 12, 15, 16, 20))
        val anxiety = subscale(answers, intArrayOf(1, 3, 6, 8, 14, 18, 19))
        val stress = subscale(answers, intArrayOf(0, 5, 7, 10, 11, 13, 17))
        val d = dassLevel(depression, intArrayOf(4, 6, 10, 13))
        val a = dassLevel(anxiety, intArrayOf(3, 5, 7, 9))
        val s = dassLevel(stress, intArrayOf(7, 9, 12, 16))
        return if (english) {
            "Depression $depression / 21 (${listOf("normal", "mild", "moderate", "severe", "extremely severe")[d]}) · " +
                "Anxiety $anxiety / 21 (${listOf("normal", "mild", "moderate", "severe", "extremely severe")[a]}) · " +
                "Stress $stress / 21 (${listOf("normal", "mild", "moderate", "severe", "extremely severe")[s]})"
        } else {
            "抑郁 $depression / 21（${listOf("正常", "轻度", "中度", "重度", "极重度")[d]}） · " +
                "焦虑 $anxiety / 21（${listOf("正常", "轻度", "中度", "重度", "极重度")[a]}） · " +
                "压力 $stress / 21（${listOf("正常", "轻度", "中度", "重度", "极重度")[s]}）"
        }
    }

    private fun psqiLatencyComponent(answers: List<Int>): Int {
        val raw = optionValue(1, answers.getOrElse(1) { 0 }) +
            optionValue(4, answers.getOrElse(4) { 0 })
        return when {
            raw == 0 -> 0
            raw <= 2 -> 1
            raw <= 4 -> 2
            else -> 3
        }
    }

    private fun psqiEfficiencyComponent(answers: List<Int>): Int {
        val bedtime = optionValue(0, answers.getOrElse(0) { 0 })
        val wake = optionValue(2, answers.getOrElse(2) { 0 })
        val minutesInBed = if (wake > bedtime) wake - bedtime else wake + 1440 - bedtime
        if (minutesInBed <= 0) return 0
        val sleepHalfHours = when (optionValue(3, answers.getOrElse(3) { 0 })) {
            0 -> 15.0
            1 -> 13.0
            2 -> 11.0
            else -> 9.0
        }
        val sleepMinutes = minOf(sleepHalfHours * 30.0, minutesInBed.toDouble())
        val efficiency = sleepMinutes / minutesInBed
        return when {
            efficiency >= 0.85 -> 0
            efficiency >= 0.75 -> 1
            efficiency >= 0.65 -> 2
            else -> 3
        }
    }

    private fun psqiDisturbanceComponent(answers: List<Int>): Int {
        val disturbances = (5..13).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
        return when {
            disturbances == 0 -> 0
            disturbances <= 9 -> 1
            disturbances <= 18 -> 2
            else -> 3
        }
    }

    private fun psqiDaytimeComponent(answers: List<Int>): Int {
        val raw = (15..16).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
        return when {
            raw == 0 -> 0
            raw <= 2 -> 1
            raw <= 4 -> 2
            else -> 3
        }
    }

    private fun psqiScore(answers: List<Int>): Int =
        optionValue(17, answers.getOrElse(17) { 0 }) +
            psqiLatencyComponent(answers) +
            optionValue(3, answers.getOrElse(3) { 0 }).coerceIn(0, 3) +
            psqiEfficiencyComponent(answers) +
            psqiDisturbanceComponent(answers) +
            optionValue(14, answers.getOrElse(14) { 0 }).coerceIn(0, 3) +
            psqiDaytimeComponent(answers)

    private fun psqiDetail(answers: List<Int>, english: Boolean): String {
        val quality = optionValue(17, answers.getOrElse(17) { 0 })
        val latency = psqiLatencyComponent(answers)
        val duration = optionValue(3, answers.getOrElse(3) { 0 }).coerceIn(0, 3)
        val efficiency = psqiEfficiencyComponent(answers)
        val disturbance = psqiDisturbanceComponent(answers)
        val medication = optionValue(14, answers.getOrElse(14) { 0 }).coerceIn(0, 3)
        val daytime = psqiDaytimeComponent(answers)
        return if (english) {
            "Quality $quality · Latency $latency · Duration $duration · Efficiency $efficiency · " +
                "Disturbance $disturbance · Medication $medication · Daytime $daytime (each 0–3; total ${psqiScore(answers)}/21)"
        } else {
            "主观质量 $quality · 入睡潜伏期 $latency · 睡眠时长 $duration · 睡眠效率 $efficiency · " +
                "睡眠紊乱 $disturbance · 助眠药物 $medication · 日间功能 $daytime（各 0–3 分；总分 ${psqiScore(answers)}/21）"
        }
    }
}

object ScaleCatalog {

    private fun o(zh: String, en: String, value: Int) = ScaleOption(zh, en, value)
    private fun q(zh: String, en: String, options: List<ScaleOption>) = ScaleQuestion(zh, en, options)
    private fun b(
        min: Int,
        max: Int,
        key: String,
        label: String,
        labelEn: String,
        advice: String,
        adviceEn: String
    ) = SeverityBand(min, max, key, label, labelEn, advice, adviceEn)

    private val phqFreq = listOf(
        o("完全不会", "Not at all", 0),
        o("好几天", "Several days", 1),
        o("一半以上的天数", "More than half the days", 2),
        o("几乎每天", "Nearly every day", 3)
    )

    private val yesNo = listOf(o("是", "Yes", 1), o("否", "No", 0))
    private val asqNoFirst = listOf(o("否", "No", 0), o("是", "Yes", 1))
    private val yesNoNoPoint = listOf(o("是", "Yes", 0), o("否", "No", 1))

    private val pclOptions = listOf(
        o("完全没有", "Not at all", 0),
        o("有一点", "A little bit", 1),
        o("中等程度", "Moderately", 2),
        o("相当明显", "Quite a bit", 3),
        o("非常严重", "Extremely", 4)
    )

    private val ociOptions = listOf(
        o("完全没有", "Not at all", 0),
        o("有一点", "A little", 1),
        o("中等程度", "Moderately", 2),
        o("很符合", "A lot", 3),
        o("非常符合", "Extremely", 4)
    )

    private val itqOptions = pclOptions

    private val isiOptions = listOf(
        o("没有", "None", 0),
        o("轻度", "Mild", 1),
        o("中度", "Moderate", 2),
        o("重度", "Severe", 3),
        o("非常严重", "Very severe", 4)
    )

    private val asrmOptions = listOf(
        o("与平时一样", "Same as usual", 0),
        o("轻微增加", "Slightly increased", 1),
        o("中度增加", "Moderately increased", 2),
        o("明显增加", "Markedly increased", 3),
        o("极度增加", "Extremely increased", 4)
    )

    private val who5Options = listOf(
        o("所有时间", "All of the time", 5),
        o("大部分时间", "Most of the time", 4),
        o("超过一半时间", "More than half of the time", 3),
        o("少于一半时间", "Less than half of the time", 2),
        o("有时", "Some of the time", 1),
        o("从未", "At no time", 0)
    )

    private val psqiBedtimeOptions = listOf(
        o("20:00 之前", "Before 20:00", 1200),
        o("20:00–21:00", "20:00–21:00", 1230),
        o("21:00–22:00", "21:00–22:00", 1290),
        o("22:00–23:00", "22:00–23:00", 1350),
        o("23:00–00:00", "23:00–00:00", 1410),
        o("00:00–01:00", "00:00–01:00", 30),
        o("01:00–02:00", "01:00–02:00", 90),
        o("02:00 之后", "After 02:00", 150)
    )

    private val psqiWakeOptions = listOf(
        o("04:00 之前", "Before 04:00", 240),
        o("04:00–05:00", "04:00–05:00", 270),
        o("05:00–06:00", "05:00–06:00", 330),
        o("06:00–07:00", "06:00–07:00", 390),
        o("07:00–08:00", "07:00–08:00", 450),
        o("08:00–09:00", "08:00–09:00", 510),
        o("09:00 之后", "After 09:00", 570)
    )

    private val psqiLatencyOptions = listOf(
        o("15 分钟以内", "15 minutes or less", 0),
        o("16–30 分钟", "16–30 minutes", 1),
        o("31–60 分钟", "31–60 minutes", 2),
        o("超过 60 分钟", "More than 60 minutes", 3)
    )

    private val psqiDurationOptions = listOf(
        o("7 小时或以上", "7 hours or more", 0),
        o("6–7 小时", "6–7 hours", 1),
        o("5–6 小时", "5–6 hours", 2),
        o("少于 5 小时", "Less than 5 hours", 3)
    )

    private val psqiFreqOptions = listOf(
        o("从未", "Not during the past month", 0),
        o("少于每周一次", "Less than once a week", 1),
        o("每周 1–2 次", "Once or twice a week", 2),
        o("每周 3 次以上", "Three or more times a week", 3)
    )

    private val agreeScale = listOf(
        o("非常同意", "Strongly agree", 3),
        o("同意", "Agree", 2),
        o("不同意", "Disagree", 1),
        o("非常不同意", "Strongly disagree", 0)
    )

    private val agreeScaleReversed = listOf(
        o("非常同意", "Strongly agree", 0),
        o("同意", "Agree", 1),
        o("不同意", "Disagree", 2),
        o("非常不同意", "Strongly disagree", 3)
    )

    private val aqAgree = listOf(
        o("非常同意", "Definitely agree", 1),
        o("有些同意", "Slightly agree", 1),
        o("有些不同意", "Slightly disagree", 0),
        o("非常不同意", "Definitely disagree", 0)
    )

    private val aqDisagree = listOf(
        o("非常同意", "Definitely agree", 0),
        o("有些同意", "Slightly agree", 0),
        o("有些不同意", "Slightly disagree", 1),
        o("非常不同意", "Definitely disagree", 1)
    )

    // ---------- 抑郁与焦虑 ----------

    val phq9 = ScaleDef(
        type = "PHQ9",
        name = "患者健康问卷（PHQ-9）",
        nameEn = "Patient Health Questionnaire (PHQ-9)",
        shortName = "PHQ-9",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "9 项抑郁自评，适用于过去两周。",
        descriptionEn = "A 9-item self-report depression measure for the past two weeks.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "在过去两周里，以下问题困扰您的频率是多少？请按第一直觉作答。",
        instructionEn = "Over the past two weeks, how often have you been bothered by the following problems? Answer with your first instinct.",
        questions = listOf(
            q("做事时提不起劲或没有兴趣", "Little interest or pleasure in doing things", phqFreq),
            q("感到心情低落、沮丧或绝望", "Feeling down, depressed or hopeless", phqFreq),
            q("入睡困难、睡不安稳或睡眠过多", "Trouble falling or staying asleep, or sleeping too much", phqFreq),
            q("感觉疲倦或没有活力", "Feeling tired or having little energy", phqFreq),
            q("食欲不振或吃太多", "Poor appetite or overeating", phqFreq),
            q("觉得自己很糟，或觉得自己很失败，或让自己或家人失望", "Feeling bad about yourself, or that you are a failure, or have let yourself or your family down", phqFreq),
            q("对事物专注有困难，例如阅读或看电视时", "Trouble concentrating on things, such as reading or watching TV", phqFreq),
            q("动作或说话速度缓慢到别人已经察觉；或相反，烦躁、坐立不安、动来动去的情况比平时更多", "Moving or speaking so slowly that others notice; or the opposite, being so fidgety or restless that you move around more than usual", phqFreq),
            q("有不如死掉或用某种方式伤害自己的念头", "Thoughts that you would be better off dead, or of hurting yourself in some way", phqFreq)
        ),
        bands = listOf(
            b(0, 4, "minimal", "无或极轻", "None / minimal", "目前的情绪负担较轻。继续保持规律的记录和睡眠。", "Your current mood burden appears light. Keep up regular tracking and sleep."),
            b(5, 9, "mild", "轻度", "Mild", "有一些低落信号。可以留意睡眠、活动量和支持系统，必要时和信任的人聊聊。", "There are some signs of low mood. Pay attention to sleep, activity and support, and talk with someone you trust if needed."),
            b(10, 14, "moderate", "中度", "Moderate", "情绪负担已经比较明显。建议把这次结果带给医生或心理咨询师讨论。", "The mood burden is noticeable. Consider sharing this result with a doctor or counsellor."),
            b(15, 19, "moderately_severe", "中重度", "Moderately severe", "这段时间可能比较艰难。建议尽快联系专业人员，并让信任的人知道你的状态。", "This may be a difficult period. Consider contacting a professional soon and letting someone you trust know how you are doing."),
            b(20, 27, "severe", "重度", "Severe", "你正在承受很多。建议尽快寻求专业帮助；如果出现伤害自己的念头，请立即联系急救或危机热线。", "You are carrying a lot. Consider seeking professional help soon; if you have thoughts of harming yourself, contact emergency services or a crisis line immediately.")
        ),
        estimatedMinutes = 3,
        featured = true,
        source = "PHQ-9: Kroenke, Spitzer & Williams (2001). 可免费用于临床与研究。",
        sourceEn = "PHQ-9: Kroenke, Spitzer & Williams (2001). Free for clinical and research use.",
        selfHarmQuestionIndex = 8
    )

    val gad7 = ScaleDef(
        type = "GAD7",
        name = "广泛性焦虑量表（GAD-7）",
        nameEn = "Generalized Anxiety Disorder scale (GAD-7)",
        shortName = "GAD-7",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "7 项焦虑自评，适用于过去两周。",
        descriptionEn = "A 7-item self-report anxiety measure for the past two weeks.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "在过去两周里，以下问题困扰您的频率是多少？请按第一直觉作答。",
        instructionEn = "Over the past two weeks, how often have you been bothered by the following problems? Answer with your first instinct.",
        questions = listOf(
            q("感到紧张、焦虑或急躁", "Feeling nervous, anxious or on edge", phqFreq),
            q("无法停止或控制担忧", "Not being able to stop or control worrying", phqFreq),
            q("对各种各样的事情担忧过多", "Worrying too much about different things", phqFreq),
            q("很难放松下来", "Trouble relaxing", phqFreq),
            q("感到坐立不安，难以安静下来", "Being so restless that it is hard to sit still", phqFreq),
            q("变得容易烦恼或急躁", "Becoming easily annoyed or irritable", phqFreq),
            q("感到似乎将有可怕的事情发生而害怕", "Feeling afraid as if something awful might happen", phqFreq)
        ),
        bands = listOf(
            b(0, 4, "minimal", "无或极轻", "None / minimal", "目前的焦虑负担较轻。继续保持规律作息和记录。", "Your current anxiety burden appears light. Keep up regular routines and tracking."),
            b(5, 9, "mild", "轻度", "Mild", "有一些焦虑信号。可以尝试放慢节奏、规律呼吸，并留意触发情境。", "There are some signs of anxiety. Try slowing down, breathing regularly, and noticing triggering situations."),
            b(10, 14, "moderate", "中度", "Moderate", "焦虑已经影响到一些日常。建议和医生或心理咨询师讨论。", "Anxiety is affecting some daily life. Consider discussing it with a doctor or counsellor."),
            b(15, 21, "severe", "重度", "Severe", "焦虑负担较重。建议尽快联系专业人员，获得更具体的支持。", "The anxiety burden is heavy. Consider contacting a professional soon for more specific support.")
        ),
        estimatedMinutes = 3,
        featured = true,
        source = "GAD-7: Spitzer, Kroenke, Williams & Löwe (2006). 可免费用于临床与研究。",
        sourceEn = "GAD-7: Spitzer, Kroenke, Williams & Löwe (2006). Free for clinical and research use."
    )

    val phq2 = ScaleDef(
        type = "PHQ2",
        name = "超短抑郁筛查（PHQ-2）",
        nameEn = "Ultra-brief depression screen (PHQ-2)",
        shortName = "PHQ-2",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "2 项快速筛查，适合在时间有限时使用。",
        descriptionEn = "A 2-item quick screen for when time is limited.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "在过去两周里，以下问题困扰您的频率是多少？",
        instructionEn = "Over the past two weeks, how often have you been bothered by the following problems?",
        questions = listOf(
            q("做事时提不起劲或没有兴趣", "Little interest or pleasure in doing things", phqFreq),
            q("感到心情低落、沮丧或绝望", "Feeling down, depressed or hopeless", phqFreq)
        ),
        bands = listOf(
            b(0, 2, "negative", "筛查阴性", "Negative screen", "目前没有明显的抑郁筛查信号。", "No clear depression signal on this screen."),
            b(3, 6, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 3 分及以上。建议使用 PHQ-9 或与专业人员进一步讨论。", "A score of 3 or more suggests further assessment with PHQ-9 or a professional.")
        ),
        estimatedMinutes = 1,
        featured = true,
        source = "PHQ-2: Kroenke, Spitzer & Williams. 可免费使用。",
        sourceEn = "PHQ-2: Kroenke, Spitzer & Williams. Free to use."
    )

    val gad2 = ScaleDef(
        type = "GAD2",
        name = "超短焦虑筛查（GAD-2）",
        nameEn = "Ultra-brief anxiety screen (GAD-2)",
        shortName = "GAD-2",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "2 项快速筛查，适合在时间有限时使用。",
        descriptionEn = "A 2-item quick screen for when time is limited.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "在过去两周里，以下问题困扰您的频率是多少？",
        instructionEn = "Over the past two weeks, how often have you been bothered by the following problems?",
        questions = listOf(
            q("感到紧张、焦虑或急躁", "Feeling nervous, anxious or on edge", phqFreq),
            q("无法停止或控制担忧", "Not being able to stop or control worrying", phqFreq)
        ),
        bands = listOf(
            b(0, 2, "negative", "筛查阴性", "Negative screen", "目前没有明显的焦虑筛查信号。", "No clear anxiety signal on this screen."),
            b(3, 6, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 3 分及以上。建议使用 GAD-7 或与专业人员进一步讨论。", "A score of 3 or more suggests further assessment with GAD-7 or a professional.")
        ),
        estimatedMinutes = 1,
        featured = true,
        source = "GAD-2: Kroenke, Spitzer, Williams et al. 可免费使用。",
        sourceEn = "GAD-2: Kroenke, Spitzer, Williams et al. Free to use."
    )

    val phq15 = ScaleDef(
        type = "PHQ15",
        name = "躯体症状量表（PHQ-15）",
        nameEn = "Somatic Symptom Scale (PHQ-15)",
        shortName = "PHQ-15",
        category = "身体与功能",
        categoryEn = "Body & functioning",
        description = "15 项常见躯体症状，了解身体不适对生活的影响。",
        descriptionEn = "15 common physical symptoms and how much they affect you.",
        timeframe = "过去四周",
        timeframeEn = "Over the past four weeks",
        instruction = "在过去四周里，下列症状困扰您的程度如何？",
        instructionEn = "Over the past four weeks, how much have you been bothered by each of the following symptoms?",
        questions = listOf(
            q("胃痛", "Stomach pain", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("背痛", "Back pain", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("手臂、腿或关节疼痛", "Pain in your arms, legs or joints", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("月经痛或其他月经问题", "Menstrual cramps or other problems with your periods", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("头痛", "Headaches", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("胸痛", "Chest pain", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("头晕", "Dizziness", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("晕厥", "Fainting spells", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("心跳加快或心悸", "Feeling your heart pound or race", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("气短", "Shortness of breath", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("性交时疼痛或不适", "Pain or problems during sexual intercourse", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("便秘、腹泻或排便问题", "Constipation, loose bowels or diarrhoea", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("恶心、胀气或消化不良", "Nausea, gas or indigestion", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("疲倦或精力不足", "Feeling tired or having low energy", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2))),
            q("睡眠困难", "Trouble sleeping", listOf(o("没有", "Not at all", 0), o("有一点", "Bothered a little", 1), o("很严重", "Bothered a lot", 2)))
        ),
        bands = listOf(
            b(0, 4, "minimal", "无或极轻", "None / minimal", "躯体症状负担较轻。", "Your physical symptom burden appears light."),
            b(5, 9, "mild", "轻度", "Mild", "有一些躯体不适。可以记录出现频率，并在复诊时带给医生。", "There are some physical symptoms. Track how often they occur and mention them at your next visit."),
            b(10, 14, "moderate", "中度", "Moderate", "躯体不适已经比较明显。建议和医生讨论，排除其他身体原因。", "Physical symptoms are noticeable. Discuss them with a doctor and rule out other medical causes."),
            b(15, 30, "severe", "重度", "Severe", "躯体症状负担较重。建议尽快就医评估，不要只归因于情绪。", "The physical symptom burden is heavy. Seek medical assessment soon and do not assume it is only emotional.")
        ),
        estimatedMinutes = 3,
        featured = true,
        source = "PHQ-15: Kroenke, Spitzer & Williams (2002). 可免费使用。",
        sourceEn = "PHQ-15: Kroenke, Spitzer & Williams (2002). Free to use."
    )

    val mdq = ScaleDef(
        type = "MDQ",
        name = "心境障碍问卷（MDQ）",
        nameEn = "Mood Disorder Questionnaire (MDQ)",
        shortName = "MDQ",
        category = "双相",
        categoryEn = "Bipolar",
        description = "双相谱系筛查，关注躁狂/轻躁狂体验。",
        descriptionEn = "A bipolar spectrum screen focused on manic and hypomanic experiences.",
        timeframe = "曾经出现",
        timeframeEn = "Ever experienced",
        instruction = "下列情况是否曾经在一段时间内同时出现？请按实际情况作答。",
        instructionEn = "Have several of the following ever happened during the same period? Answer as accurately as you can.",
        questions = listOf(
            q("有一段时间你感觉特别好或特别兴奋，别人觉得你和平常不一样，甚至因此惹上麻烦", "You had a period when you felt so good or hyper that other people thought you were not your normal self, or you got into trouble", yesNo),
            q("你特别容易发脾气，对别人大喊大叫或与人争吵", "You were so irritable that you shouted at people or started fights or arguments", yesNo),
            q("你比平时自信得多", "You felt much more self-confident than usual", yesNo),
            q("你睡得比平时少很多，却并不觉得困", "You got much less sleep than usual and did not really miss it", yesNo),
            q("你比平时话多，或说话速度快很多", "You were much more talkative or spoke faster than usual", yesNo),
            q("思绪奔涌，脑子停不下来", "Thoughts raced through your head or you could not slow your mind down", yesNo),
            q("很容易被周围的事情分心，难以集中", "You were so easily distracted that you had trouble concentrating or staying on track", yesNo),
            q("精力比平时旺盛很多", "You had much more energy than usual", yesNo),
            q("比平时活跃很多，做了更多事情", "You were much more active or did many more things than usual", yesNo),
            q("比平时更爱社交、更外向，例如半夜给朋友打电话", "You were much more social or outgoing than usual, for example telephoning friends in the middle of the night", yesNo),
            q("对性的兴趣比平时明显增加", "You were much more interested in sex than usual", yesNo),
            q("做了一些平时不会做、别人可能觉得过分、愚蠢或冒险的事", "You did things that were unusual for you or that other people might have thought were excessive, foolish or risky", yesNo),
            q("花钱让你或家人陷入麻烦", "Spending money got you or your family into trouble", yesNo),
            q("上面有不止一项同时出现过", "More than one of the above happened during the same period", yesNo),
            q("这些问题对你的生活造成了多大影响？", "How much of a problem did these cause you?", listOf(
                o("没有影响", "No problem", 0),
                o("轻微影响", "Minor problem", 1),
                o("中度影响", "Moderate problem", 2),
                o("严重影响", "Serious problem", 3)
            ))
        ),
        bands = listOf(
            b(0, 6, "negative", "筛查阴性", "Negative screen", "目前没有达到 MDQ 的阳性筛查标准。", "The MDQ screen is currently negative."),
            b(7, 13, "partial", "部分符合", "Partial criteria", "症状条目较多，但未同时满足「同一时期」和「造成中度以上影响」两个条件。建议把结果带给专业人员讨论。", "Several symptom items are present, but the same-period and moderate-impairment criteria are not both met. Discuss this with a professional."),
            b(7, 13, "positive", "筛查阳性", "Positive screen", "达到 MDQ 阳性筛查标准。这不等于双相诊断，建议尽快由精神科专业人员评估。", "The MDQ positive screen criteria are met. This is not a diagnosis of bipolar disorder; seek assessment by a mental health professional.")
        ),
        scoring = ScaleScoring.MDQ,
        maxScoreOverride = 13,
        estimatedMinutes = 4,
        featured = true,
        source = "MDQ: Hirschfeld et al. (2000). 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "MDQ: Hirschfeld et al. (2000). Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "MDQ 是筛查工具，不能替代双相障碍的临床诊断。",
        noteEn = "The MDQ is a screening tool and cannot replace a clinical diagnosis of bipolar disorder."
    )

    val asrm = ScaleDef(
        type = "ASRM",
        name = "Altman 躁狂自评量表（ASRM）",
        nameEn = "Altman Self-Rating Mania Scale (ASRM)",
        shortName = "ASRM",
        category = "双相",
        categoryEn = "Bipolar",
        description = "5 项自评，了解当前躁狂/轻躁狂相关状态。",
        descriptionEn = "A 5-item self-report of current manic and hypomanic symptoms.",
        timeframe = "当前",
        timeframeEn = "Right now",
        instruction = "请选择一个最符合你当前状态的选项。",
        instructionEn = "Choose the option that best describes how you feel right now.",
        questions = listOf(
            q("与平时的自己相比，你现在的情绪如何？", "Compared with your usual self, how is your mood now?", listOf(
                o("与平时一样", "Same as usual", 0),
                o("稍微高涨", "Slightly more upbeat", 1),
                o("中度高涨", "Moderately more upbeat", 2),
                o("明显高涨", "Markedly more upbeat", 3),
                o("极度高涨", "Extremely more upbeat", 4)
            )),
            q("你现在的说话量如何？", "How much are you talking now?", listOf(
                o("与平时一样", "Normal", 0),
                o("稍微多一些", "Slightly more talkative", 1),
                o("中度增多", "Moderately more talkative", 2),
                o("明显增多", "Markedly more talkative", 3),
                o("极度增多", "Extremely talkative", 4)
            )),
            q("你现在的活动量如何？", "How much are you doing now?", listOf(
                o("与平时一样", "Normal", 0),
                o("稍微多一些", "Slightly more active", 1),
                o("中度增多", "Moderately more active", 2),
                o("明显增多", "Markedly more active", 3),
                o("极度增多", "Extremely active", 4)
            )),
            q("你现在的睡眠需求如何？", "How much are you sleeping now?", listOf(
                o("与平时一样", "Normal", 0),
                o("稍微减少", "Slightly less sleep", 1),
                o("中度减少", "Moderately less sleep", 2),
                o("明显减少", "Markedly less sleep", 3),
                o("几乎不睡", "Very little sleep", 4)
            )),
            q("你现在有多易怒？", "How irritable are you now?", listOf(
                o("完全不", "Not at all", 0),
                o("轻微", "Slightly irritable", 1),
                o("中度", "Moderately irritable", 2),
                o("明显", "Very irritable", 3),
                o("极度", "Extremely irritable", 4)
            ))
        ),
        bands = listOf(
            b(0, 5, "negative", "无明显躁狂信号", "No clear manic signal", "当前状态未达到 ASRM 的筛查界值。", "Your current score is below the ASRM screening threshold."),
            b(6, 20, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 6 分及以上。建议尽快与专业人员讨论，尤其是睡眠需求明显减少时。", "A score of 6 or more suggests further assessment, especially if your need for sleep is markedly reduced. Discuss this with a professional.")
        ),
        estimatedMinutes = 1,
        featured = true,
        source = "ASRM: Altman et al. (1997). 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "ASRM: Altman et al. (1997). Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "ASRM 不是双相障碍的诊断工具。",
        noteEn = "The ASRM is not a diagnostic tool for bipolar disorder."
    )

    val ocir = ScaleDef(
        type = "OCIR",
        name = "强迫症量表（OCI-R）",
        nameEn = "Obsessive-Compulsive Inventory-Revised (OCI-R)",
        shortName = "OCI-R",
        category = "强迫",
        categoryEn = "OCD",
        description = "18 项自评，了解强迫思维与行为带来的困扰。",
        descriptionEn = "An 18-item self-report of distress from obsessive-compulsive thoughts and behaviours.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "过去一个月里，下列情况让你感到困扰的程度如何？",
        instructionEn = "Over the past month, how much have you been bothered by the following?",
        questions = listOf(
            q("我存了太多东西，以至于它们妨碍了生活", "I have saved up so many things that they get in the way", ociOptions),
            q("我检查东西的次数比必要的多", "I check things more often than necessary", ociOptions),
            q("如果物品没有摆放整齐，我会很难受", "I get upset if objects are not arranged properly", ociOptions),
            q("做事情时，我觉得必须数数", "I feel compelled to count while I am doing things", ociOptions),
            q("如果物品被陌生人碰过，我会很难去碰它", "I find it difficult to touch an object when I know it has been touched by strangers or certain people", ociOptions),
            q("我觉得很难控制自己的想法", "I find it difficult to control my own thoughts", ociOptions),
            q("我会收集自己并不需要的物品", "I collect things I don't need", ociOptions),
            q("我会反复检查门、窗、抽屉等", "I repeatedly check doors, windows, drawers, etc.", ociOptions),
            q("别人改变我摆放物品的方式会让我难受", "I get upset if others change the way I have arranged things", ociOptions),
            q("我觉得自己必须重复某些数字", "I feel I have to repeat certain numbers", ociOptions),
            q("有时我仅仅因为觉得被污染就要洗手或清洁", "I sometimes have to wash or clean myself simply because I feel contaminated", ociOptions),
            q("不愉快的想法会不受控制地进入我的脑海，让我难受", "I am upset by unpleasant thoughts that come into my mind against my will", ociOptions),
            q("因为怕以后会用到，我不敢扔东西", "I avoid throwing things away because I am afraid I might need them later", ociOptions),
            q("关掉燃气、水龙头和灯后，我会反复检查", "I repeatedly check gas and water taps and light switches after turning them off", ociOptions),
            q("我需要物品按特定顺序摆放", "I need things to be arranged in a particular order", ociOptions),
            q("我觉得数字有“好”和“坏”之分", "I feel that there are good and bad numbers", ociOptions),
            q("我洗手的次数比必要的多，时间也更长", "I wash my hands more often and longer than necessary", ociOptions),
            q("我经常出现令人讨厌的想法，且难以摆脱", "I frequently get nasty thoughts and have difficulty in getting rid of them", ociOptions)
        ),
        bands = listOf(
            b(0, 20, "below", "低于筛查界值", "Below screening threshold", "得分低于 OCI-R 常用的筛查界值（21 分）。如果强迫症状仍然困扰你，可以记录具体情境并和专业人员讨论。", "Your score is below the common OCI-R screening threshold of 21. If symptoms still bother you, track the situations and discuss them with a professional."),
            b(21, 72, "above", "达到筛查界值", "At or above screening threshold", "得分达到 21 分及以上。这提示强迫症状值得进一步评估，但不等于强迫症诊断。", "A score of 21 or more suggests further assessment for OCD. This is not a diagnosis.")
        ),
        estimatedMinutes = 5,
        featured = true,
        source = "OCI-R: Foa et al. (2002). 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "OCI-R: Foa et al. (2002). Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "OCI-R 不是强迫症的诊断工具。",
        noteEn = "The OCI-R is not a diagnostic tool for OCD."
    )

    val pcptsd5 = ScaleDef(
        type = "PCPTSD5",
        name = "创伤后应激初筛（PC-PTSD-5）",
        nameEn = "Primary Care PTSD Screen for DSM-5 (PC-PTSD-5)",
        shortName = "PC-PTSD-5",
        category = "创伤与应激",
        categoryEn = "Trauma & stress",
        description = "5 项超短筛查，适合先判断是否需要进一步评估。",
        descriptionEn = "A 5-item brief screen to decide whether further assessment is needed.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "请先回想一件让你感到非常害怕、无助或恐惧的经历，然后回答过去一个月的情况。",
        instructionEn = "First bring to mind an experience that was very frightening, helpless or horrifying, then answer for the past month.",
        questions = listOf(
            q("做过关于那件事的噩梦，或不由自主地想起它", "Had nightmares about the event or thought about it when you did not want to", yesNo),
            q("努力不去想那件事，或回避会让你想起它的情境", "Tried hard not to think about the event or avoided situations that reminded you of it", yesNo),
            q("一直保持警惕、警觉，或很容易被吓到", "Been constantly on guard, watchful or easily startled", yesNo),
            q("感到麻木，或与周围的人、活动、环境疏远", "Felt numb or detached from people, activities or your surroundings", yesNo),
            q("感到内疚，或无法停止责怪自己或他人", "Felt guilty or unable to stop blaming yourself or others for the event or its aftermath", yesNo)
        ),
        bands = listOf(
            b(0, 2, "negative", "筛查阴性", "Negative screen", "目前未达到 PC-PTSD-5 的筛查界值。", "Your score is below the PC-PTSD-5 screening threshold."),
            b(3, 5, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 3 分及以上。建议使用 PCL-5 或与专业人员进一步讨论。这不是 PTSD 诊断。", "A score of 3 or more suggests further assessment with PCL-5 or a professional. This is not a diagnosis of PTSD.")
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "PC-PTSD-5: U.S. Department of Veterans Affairs. 公共领域。",
        sourceEn = "PC-PTSD-5: U.S. Department of Veterans Affairs. Public domain.",
        note = "如果你没有经历过创伤事件，这项量表可能不适用。",
        noteEn = "If you have not experienced a traumatic event, this screen may not apply."
    )

    val pcl5 = ScaleDef(
        type = "PCL5",
        name = "PTSD 症状清单（PCL-5）",
        nameEn = "PTSD Checklist for DSM-5 (PCL-5)",
        shortName = "PCL-5",
        category = "创伤与应激",
        categoryEn = "Trauma & stress",
        description = "20 项自评，了解创伤后症状的严重程度。",
        descriptionEn = "A 20-item self-report of post-traumatic symptom severity.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "请针对一件让你感到非常害怕、无助或恐惧的经历，回答过去一个月被下列问题困扰的程度。",
        instructionEn = "Keeping in mind an experience that was very frightening, helpless or horrifying, indicate how much you have been bothered by each problem in the past month.",
        questions = listOf(
            q("反复出现、令人不安的创伤记忆", "Repeated, disturbing and unwanted memories of the stressful experience", pclOptions),
            q("反复出现、令人不安的创伤梦境", "Repeated, disturbing dreams of the stressful experience", pclOptions),
            q("突然感觉或表现得像创伤事件再次发生", "Suddenly feeling or acting as if the stressful experience were actually happening again", pclOptions),
            q("遇到提醒创伤的事物时感到非常难受", "Feeling very upset when something reminded you of the stressful experience", pclOptions),
            q("遇到提醒创伤的事物时出现强烈身体反应", "Having strong physical reactions when something reminded you of the stressful experience", pclOptions),
            q("回避与创伤有关的记忆、想法或感受", "Avoiding memories, thoughts or feelings related to the stressful experience", pclOptions),
            q("回避会提醒你创伤事件的外部事物", "Avoiding external reminders of the stressful experience", pclOptions),
            q("难以记住创伤事件的重要部分", "Trouble remembering important parts of the stressful experience", pclOptions),
            q("对自己、他人或世界有强烈的负面信念", "Having strong negative beliefs about yourself, other people or the world", pclOptions),
            q("因创伤事件或其后发生的事责怪自己或他人", "Blaming yourself or someone else for the stressful experience or what happened after it", pclOptions),
            q("有强烈的负面情绪，如恐惧、惊恐、愤怒、内疚或羞耻", "Having strong negative feelings such as fear, horror, anger, guilt or shame", pclOptions),
            q("对以前喜欢的活动失去兴趣", "Loss of interest in activities that you used to enjoy", pclOptions),
            q("感到与他人疏远或隔绝", "Feeling distant or cut off from other people", pclOptions),
            q("难以体验积极情绪", "Trouble experiencing positive feelings", pclOptions),
            q("易怒、发脾气或攻击性行为", "Irritable behaviour, angry outbursts or acting aggressively", pclOptions),
            q("冒太多风险，或做可能伤害自己的事", "Taking too many risks or doing things that could cause you harm", pclOptions),
            q("过度警觉、保持警惕", "Being \"super-alert\" or watchful or on guard", pclOptions),
            q("容易受惊或跳起来", "Feeling jumpy or easily startled", pclOptions),
            q("难以集中注意力", "Having difficulty concentrating", pclOptions),
            q("入睡困难或睡不安稳", "Trouble falling or staying asleep", pclOptions)
        ),
        bands = listOf(
            b(0, 30, "below", "低于参考界值", "Below reference threshold", "得分低于 PCL-5 常用的参考界值（31–33 分）。如果症状仍然困扰你，可以继续记录并和专业人员讨论。", "Your score is below the common PCL-5 reference threshold of 31–33. If symptoms still bother you, keep tracking and discuss them with a professional."),
            b(31, 49, "possible", "可能达到 PTSD 筛查标准", "Possible PTSD screen", "得分在 31–49 之间。建议由专业人员进一步评估；这不是 PTSD 诊断。", "A score between 31 and 49 suggests further assessment by a professional. This is not a diagnosis of PTSD."),
            b(50, 80, "high", "症状负担较高", "High symptom burden", "得分达到 50 分及以上。建议尽快与专业人员讨论，并优先关注安全与支持。", "A score of 50 or more suggests discussing this with a professional soon, with priority on safety and support.")
        ),
        estimatedMinutes = 6,
        featured = true,
        source = "PCL-5: U.S. Department of Veterans Affairs, National Center for PTSD. 公共领域。",
        sourceEn = "PCL-5: U.S. Department of Veterans Affairs, National Center for PTSD. Public domain.",
        note = "PCL-5 用于筛查和追踪，不用于自我诊断 PTSD。",
        noteEn = "The PCL-5 is for screening and tracking, not for self-diagnosing PTSD."
    )

    val isi = ScaleDef(
        type = "ISI",
        name = "失眠严重程度指数（ISI）",
        nameEn = "Insomnia Severity Index (ISI)",
        shortName = "ISI",
        category = "睡眠",
        categoryEn = "Sleep",
        description = "7 项自评，了解失眠的严重程度与影响。",
        descriptionEn = "A 7-item self-report of insomnia severity and impact.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "请评估过去两周的睡眠情况。",
        instructionEn = "Please rate your sleep over the past two weeks.",
        questions = listOf(
            q("入睡困难", "Difficulty falling asleep", isiOptions),
            q("睡眠维持困难", "Difficulty staying asleep", isiOptions),
            q("早醒", "Problem waking up too early", isiOptions),
            q("对当前睡眠模式的满意程度", "How satisfied are you with your current sleep pattern?", listOf(
                o("非常满意", "Very satisfied", 0),
                o("满意", "Satisfied", 1),
                o("一般", "Moderately satisfied", 2),
                o("不满意", "Dissatisfied", 3),
                o("非常不满意", "Very dissatisfied", 4)
            )),
            q("睡眠问题在多大程度上被他人注意到", "How noticeable to others is your sleep problem?", listOf(
                o("完全没被注意到", "Not at all noticeable", 0),
                o("轻微", "A little", 1),
                o("有些", "Somewhat", 2),
                o("明显", "Much", 3),
                o("非常明显", "Very much noticeable", 4)
            )),
            q("你对当前睡眠问题有多担心或困扰", "How worried or distressed are you about your current sleep problem?", listOf(
                o("完全不担心", "Not at all worried", 0),
                o("有一点", "A little", 1),
                o("有些", "Somewhat", 2),
                o("很担心", "Much", 3),
                o("非常担心", "Very much worried", 4)
            )),
            q("睡眠问题在多大程度上影响你的日常功能", "To what extent does your sleep problem interfere with your daily functioning?", listOf(
                o("完全不影响", "Not at all interfering", 0),
                o("轻微", "A little", 1),
                o("有些", "Somewhat", 2),
                o("明显", "Much", 3),
                o("非常明显", "Very much interfering", 4)
            ))
        ),
        bands = listOf(
            b(0, 7, "none", "无明显失眠", "No clinically significant insomnia", "目前睡眠问题不明显。继续保持规律作息。", "There is no clinically significant insomnia at present. Keep regular sleep habits."),
            b(8, 14, "subthreshold", "亚临床失眠", "Subthreshold insomnia", "有一些失眠信号。可以调整睡眠节律、减少睡前刺激，并观察两周。", "There are some signs of insomnia. Adjust your sleep routine, reduce stimulation before bed, and observe for two weeks."),
            b(15, 21, "moderate", "中度失眠", "Moderate insomnia", "失眠已经比较明显。建议和医生或睡眠专业人员讨论。", "Insomnia is noticeable. Consider discussing it with a doctor or sleep specialist."),
            b(22, 28, "severe", "重度失眠", "Severe insomnia", "失眠负担较重。建议尽快寻求专业帮助，并避免自行长期使用助眠药物。", "The insomnia burden is heavy. Seek professional help soon and avoid long-term self-medication.")
        ),
        estimatedMinutes = 2,
        featured = true,
        source = "ISI: Morin et al. 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "ISI: Morin et al. Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "ISI 不是失眠的诊断工具。",
        noteEn = "The ISI is not a diagnostic tool for insomnia."
    )

    val who5 = ScaleDef(
        type = "WHO5",
        name = "幸福感量表（WHO-5）",
        nameEn = "Well-Being Index (WHO-5)",
        shortName = "WHO-5",
        category = "整体状态",
        categoryEn = "Overall wellbeing",
        description = "5 项主观幸福感自评，分数越高表示状态越好。",
        descriptionEn = "A 5-item measure of subjective wellbeing; higher scores mean better wellbeing.",
        timeframe = "过去两周",
        timeframeEn = "Over the past two weeks",
        instruction = "请选择过去两周最符合你状态的选项。",
        instructionEn = "Choose the option that best describes you over the past two weeks.",
        questions = listOf(
            q("我感到愉快、精神不错", "I have felt cheerful and in good spirits", who5Options),
            q("我感到平静和放松", "I have felt calm and relaxed", who5Options),
            q("我感到有活力、精力充沛", "I have felt active and vigorous", who5Options),
            q("我醒来时感到神清气爽、休息充分", "I woke up feeling fresh and rested", who5Options),
            q("我的日常生活中充满让我感兴趣的事情", "My daily life has been filled with things that interest me", who5Options)
        ),
        bands = listOf(
            b(0, 12, "low", "幸福感偏低", "Low wellbeing", "原始分折算为 50% 或以下。建议关注情绪、睡眠与支持系统，必要时和专业人员聊聊。", "The raw score is 50% or below. Pay attention to mood, sleep and support, and consider talking with a professional."),
            b(13, 17, "moderate", "中等", "Moderate", "幸福感处于中等水平。可以继续记录变化。", "Wellbeing is moderate. Keep tracking changes."),
            b(18, 25, "good", "较好", "Good", "幸福感较好。继续保持让你感到有连接和意义的活动。", "Wellbeing is good. Keep up activities that give you connection and meaning.")
        ),
        estimatedMinutes = 1,
        featured = true,
        source = "WHO-5: World Health Organization. 可免费用于非商业目的。",
        sourceEn = "WHO-5: World Health Organization. Free for non-commercial use.",
        note = "WHO-5 用于测量主观幸福感，不是抑郁诊断。",
        noteEn = "The WHO-5 measures subjective wellbeing and is not a depression diagnosis."
    )

    val whodas = ScaleDef(
        type = "WHODAS",
        name = "日常功能评估（WHODAS 2.0，12 题）",
        nameEn = "WHO Disability Assessment Schedule 2.0 (12-item)",
        shortName = "WHODAS-12",
        category = "身体与功能",
        categoryEn = "Body & functioning",
        description = "12 项自评，了解过去 30 天日常功能受健康问题影响的程度。",
        descriptionEn = "A 12-item self-report of how much health problems affected daily functioning in the past 30 days.",
        timeframe = "过去 30 天",
        timeframeEn = "Over the past 30 days",
        instruction = "在过去 30 天里，你完成下列活动时有多困难？",
        instructionEn = "In the past 30 days, how much difficulty did you have doing the following?",
        questions = listOf(
            q("长时间站立，例如 30 分钟", "Standing for long periods such as 30 minutes", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("承担家务责任", "Taking care of your household responsibilities", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("学习新任务，例如学习去一个新地方", "Learning a new task, for example learning how to get to a new place", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("参与社区活动", "Joining in community activities", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("健康问题对你的情绪影响有多大？", "How much have you been emotionally affected by your health problems?", listOf(o("没有影响", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度", "Extreme", 4))),
            q("集中注意力做一件事十分钟", "Concentrating on doing something for ten minutes", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("长距离行走，例如一公里", "Walking a long distance such as a kilometre", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("清洗全身", "Washing your whole body", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("穿衣服", "Getting dressed", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("与不认识的人打交道", "Dealing with people you do not know", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("维持友谊", "Maintaining a friendship", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4))),
            q("完成日常工作", "Your day-to-day work", listOf(o("没有困难", "None", 0), o("轻微", "Mild", 1), o("中度", "Moderate", 2), o("严重", "Severe", 3), o("极度/无法完成", "Extreme / cannot do", 4)))
        ),
        bands = listOf(
            b(0, 11, "low", "功能影响较小", "Low functional impact", "日常功能受健康问题影响较小。", "Health problems are having a low impact on daily functioning."),
            b(12, 23, "moderate", "中度功能影响", "Moderate functional impact", "日常功能受到一定影响。可以记录具体困难的场景，并在复诊时讨论。", "Daily functioning is moderately affected. Note the situations that are difficult and discuss them at your next visit."),
            b(24, 35, "high", "较高功能影响", "High functional impact", "日常功能受影响较明显。建议和专业人员讨论支持与调整。", "Daily functioning is substantially affected. Consider discussing support and adjustments with a professional."),
            b(36, 48, "very_high", "很高功能影响", "Very high functional impact", "日常功能受影响很大。建议尽快获得专业支持。", "Daily functioning is very heavily affected. Consider seeking professional support soon.")
        ),
        estimatedMinutes = 3,
        featured = false,
        source = "WHODAS 2.0: World Health Organization. 可免费用于非商业目的。",
        sourceEn = "WHODAS 2.0: World Health Organization. Free for non-commercial use.",
        note = "这里使用简化总分；完整 WHODAS 的官方计分更复杂。",
        noteEn = "This uses a simplified total score; the official WHODAS scoring is more complex."
    )

    val auditc = ScaleDef(
        type = "AUDITC",
        name = "酒精使用筛查（AUDIT-C）",
        nameEn = "Alcohol Use Disorders Identification Test – Consumption (AUDIT-C)",
        shortName = "AUDIT-C",
        category = "物质使用",
        categoryEn = "Substance use",
        description = "3 项酒精使用筛查，关注饮酒频率与量。",
        descriptionEn = "A 3-item screen for alcohol consumption and risk.",
        timeframe = "过去一年",
        timeframeEn = "Over the past year",
        instruction = "请选择最符合你过去一年饮酒情况的选项。",
        instructionEn = "Choose the option that best describes your drinking over the past year.",
        questions = listOf(
            q("你多久喝一次含酒精的饮料？", "How often do you have a drink containing alcohol?", listOf(
                o("从不", "Never", 0),
                o("每月一次或更少", "Monthly or less", 1),
                o("每月 2–4 次", "2–4 times a month", 2),
                o("每周 2–3 次", "2–3 times a week", 3),
                o("每周 4 次或更多", "4 or more times a week", 4)
            )),
            q("你喝酒的日子里，通常喝多少杯？", "How many drinks containing alcohol do you have on a typical day when you are drinking?", listOf(
                o("0–1 杯", "0–1 drinks", 0),
                o("2–3 杯", "2–3 drinks", 1),
                o("4–5 杯", "4–5 drinks", 2),
                o("6–7 杯", "6–7 drinks", 3),
                o("8 杯以上", "8 or more drinks", 4)
            )),
            q("你多久一次喝 6 杯或更多？", "How often do you have six or more drinks on one occasion?", listOf(
                o("从不", "Never", 0),
                o("少于每月一次", "Less than monthly", 1),
                o("每月一次", "Monthly", 2),
                o("每周一次", "Weekly", 3),
                o("每天或几乎每天", "Daily or almost daily", 4)
            ))
        ),
        bands = listOf(
            b(0, 2, "low", "风险较低", "Lower risk", "目前酒精使用风险较低。", "Your current alcohol use appears to be lower risk."),
            b(3, 5, "risky", "建议关注", "Worth attention", "酒精使用可能带来风险。可以记录饮酒情境，并考虑与医生讨论。", "Alcohol use may be carrying some risk. Track the situations and consider discussing it with a doctor."),
            b(6, 12, "high", "较高风险", "Higher risk", "酒精使用风险较高。建议尽快与专业人员讨论，不要自行突然停酒以免出现戒断反应。", "Alcohol use is higher risk. Consider discussing it with a professional soon; do not stop suddenly on your own because of withdrawal risk.")
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "AUDIT-C: Bush et al. / World Health Organization. 可免费用于非商业目的。",
        sourceEn = "AUDIT-C: Bush et al. / World Health Organization. Free for non-commercial use.",
        note = "不同性别的参考界值不同：女性 3 分及以上、男性 4 分及以上。",
        noteEn = "Reference cut-offs differ by sex: 3+ for women and 4+ for men."
    )

    val itq = ScaleDef(
        type = "ITQ",
        name = "国际创伤问卷（ITQ）",
        nameEn = "International Trauma Questionnaire (ITQ)",
        shortName = "ITQ",
        category = "创伤与应激",
        categoryEn = "Trauma & stress",
        description = "18 项自评，关注 ICD-11 PTSD 与复杂性 PTSD 相关体验。",
        descriptionEn = "An 18-item self-report covering ICD-11 PTSD and complex PTSD experiences.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "请针对一件让你感到非常害怕、无助或恐惧的经历，回答过去一个月被下列问题困扰的程度。",
        instructionEn = "Keeping in mind an experience that was very frightening, helpless or horrifying, indicate how much you have been bothered by each problem in the past month.",
        questions = listOf(
            q("反复出现、令人不安的创伤梦境", "Upsetting dreams that replay the event or parts of it", itqOptions),
            q("突然感觉或表现得像创伤事件再次发生", "Powerful images or memories in which you feel the event is happening again", itqOptions),
            q("回避与创伤有关的内心提醒（想法、感受、身体感觉）", "Avoiding internal reminders of the event (thoughts, feelings, sensations)", itqOptions),
            q("回避与创伤有关的外部提醒（人、地点、对话、活动、物品）", "Avoiding external reminders of the event (people, places, conversations, activities, objects)", itqOptions),
            q("一直保持高度警觉、警惕", "Being \"super-alert\", watchful or on guard", itqOptions),
            q("容易受惊或跳起来", "Feeling jumpy or easily startled", itqOptions),
            q("与创伤有关的内疚或羞耻感", "Strong feelings of shame or guilt in relation to the event", itqOptions),
            q("感到与他人疏远或隔绝", "Feeling cut off from other people", itqOptions),
            q("难以与他人保持情感连接", "Difficulty staying emotionally connected to other people", itqOptions),
            q("感到麻木或情绪关闭", "Feeling numb or emotionally shut down", itqOptions),
            q("感到自己没有价值", "Feeling worthless", itqOptions),
            q("感到自己是个失败者", "Feeling like a failure", itqOptions),
            q("这些问题影响了你的朋友关系", "Interfering with your relationships with friends", itqOptions),
            q("这些问题影响了你的家庭关系", "Interfering with your relationships with family", itqOptions),
            q("这些问题影响了你的工作或学习", "Interfering with your work or ability to study", itqOptions),
            q("这些问题影响了你的自我照顾", "Interfering with your ability to take care of yourself", itqOptions),
            q("这些问题影响了你的社交生活", "Interfering with your social life", itqOptions),
            q("这些问题影响了你的日常责任", "Interfering with your day-to-day responsibilities", itqOptions)
        ),
        bands = listOf(
            b(0, 17, "low", "症状负担较低", "Lower symptom burden", "目前的创伤相关症状负担较低。如果仍有困扰，可以继续记录。", "Current trauma-related symptom burden appears lower. Keep tracking if symptoms still bother you."),
            b(18, 35, "moderate", "中度症状负担", "Moderate symptom burden", "创伤相关症状值得关注。建议由熟悉创伤治疗的专业人员进一步评估。", "Trauma-related symptoms are worth attention. Consider assessment by a professional experienced in trauma care."),
            b(36, 53, "high", "较高症状负担", "High symptom burden", "创伤相关症状负担较高。建议尽快获得专业支持，并优先关注安全。", "Trauma-related symptom burden is high. Consider professional support soon, with priority on safety."),
            b(54, 72, "very_high", "很高症状负担", "Very high symptom burden", "症状负担很高。建议尽快联系专业人员；如果出现自伤或自杀念头，请立即寻求紧急帮助。", "Symptom burden is very high. Contact a professional soon; if you have thoughts of self-harm or suicide, seek emergency help immediately.")
        ),
        estimatedMinutes = 5,
        featured = false,
        source = "ITQ: Cloitre et al. / International Trauma Consortium. 研究与临床使用请保留来源说明。",
        sourceEn = "ITQ: Cloitre et al. / International Trauma Consortium. Keep the source attribution for research and clinical use.",
        note = "这里的总分为简化展示，不能替代 ICD-11 复杂性 PTSD 的专业评估。",
        noteEn = "The total shown here is a simplified display and cannot replace a professional assessment of ICD-11 complex PTSD."
    )

    val asrs5 = ScaleDef(
        type = "ASRS5",
        name = "成人 ADHD 简版筛查（ASRS-5）",
        nameEn = "Adult ADHD Self-Report Screening Scale (ASRS-5)",
        shortName = "ASRS-5",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "6 项成人 ADHD 快速筛查。需要完整症状清单（注意力 / 冲动多动两个维度）时，请使用下方的 ASRS-18。",
        descriptionEn = "A 6-item quick adult ADHD screen. For the full symptom checklist with inattention and hyperactivity-impulsivity subscales, use ASRS-18 below.",
        timeframe = "过去六个月",
        timeframeEn = "Over the past six months",
        instruction = "请选择过去六个月最符合你情况的频率。",
        instructionEn = "Choose the frequency that best describes you over the past six months.",
        questions = listOf(
            q("项目中最难的部分完成后，你有多难收尾？", "How often do you have trouble wrapping up the final details of a project once the challenging parts have been done?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))),
            q("需要组织任务时，你有多难把事情安排妥当？", "How often do you have difficulty getting things in order when you have to do a task that requires organisation?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))),
            q("你有多经常忘记预约或该做的事？", "How often do you have problems remembering appointments or obligations?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))),
            q("需要大量思考的任务，你有多经常拖延或回避？", "When you have a task that requires a lot of thought, how often do you avoid or delay getting started?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))),
            q("需要长时间坐着时，你有多经常手脚动来动去？", "How often do you fidget or squirm with your hands or feet when you have to sit down for a long time?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))),
            q("你有多经常感到过度活跃，像被马达驱动一样停不下来？", "How often do you feel overly active and compelled to do things, like you are driven by a motor?", listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4)))
        ),
        bands = listOf(
            b(0, 13, "below", "低于筛查界值", "Below screening threshold", "得分低于 ASRS-5 常用筛查界值（14 分）。", "Your score is below the common ASRS-5 screening threshold of 14."),
            b(14, 24, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 14 分及以上。建议由专业人员进一步评估；这不是 ADHD 诊断。", "A score of 14 or more suggests further assessment by a professional. This is not an ADHD diagnosis.")
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "ASRS-5: World Health Organization / Ustun et al. 非商业使用请保留来源。",
        sourceEn = "ASRS-5: World Health Organization / Ustun et al. Keep the source attribution for non-commercial use.",
        note = "ADHD 的评估需要结合童年起病、功能影响与专业访谈。",
        noteEn = "ADHD assessment requires childhood onset, functional impact and a professional interview."
    )

    val scoff = ScaleDef(
        type = "SCOFF",
        name = "进食障碍筛查（SCOFF）",
        nameEn = "SCOFF Eating Disorder Screen",
        shortName = "SCOFF",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "5 项快速进食障碍筛查。",
        descriptionEn = "A 5-item quick screen for eating disorders.",
        timeframe = "近期",
        timeframeEn = "Recently",
        instruction = "请根据近期情况回答是或否。",
        instructionEn = "Answer yes or no based on how things have been recently.",
        questions = listOf(
            q("你会因为觉得吃得太撑而让自己呕吐吗？", "Do you make yourself sick because you feel uncomfortably full?", yesNo),
            q("你会担心自己无法控制吃多少吗？", "Do you worry you have lost control over how much you eat?", yesNo),
            q("你最近三个月内体重下降超过 6.35 公斤吗？", "Have you recently lost more than 6.35 kg in a 3-month period?", yesNo),
            q("别人说你太瘦时，你仍然觉得自己胖吗？", "Do you believe yourself to be fat when others say you are too thin?", yesNo),
            q("你会觉得食物主宰了你的生活吗？", "Would you say that food dominates your life?", yesNo)
        ),
        bands = listOf(
            b(0, 1, "negative", "筛查阴性", "Negative screen", "目前未达到 SCOFF 的阳性筛查标准。", "The SCOFF screen is currently negative."),
            b(2, 5, "positive", "建议进一步评估", "Further assessment suggested", "有 2 项或以上回答为“是”。建议与专业人员讨论进食情况与身体状况。", "Two or more yes answers suggest discussing eating and physical health with a professional.")
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "SCOFF: Morgan, Reid & Lacey (1999). 临床与研究常用。",
        sourceEn = "SCOFF: Morgan, Reid & Lacey (1999). Commonly used in clinical care and research.",
        note = "SCOFF 不是进食障碍的诊断工具。",
        noteEn = "The SCOFF is not a diagnostic tool for eating disorders."
    )

    val epds = ScaleDef(
        type = "EPDS",
        name = "爱丁堡产后抑郁量表（EPDS）",
        nameEn = "Edinburgh Postnatal Depression Scale (EPDS)",
        shortName = "EPDS",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "10 项自评，适用于孕期及产后情绪筛查。",
        descriptionEn = "A 10-item self-report for depression screening during pregnancy and after birth.",
        timeframe = "过去 7 天",
        timeframeEn = "Over the past 7 days",
        instruction = "请选择过去 7 天最符合你状态的选项。",
        instructionEn = "Choose the option that best describes how you have felt over the past 7 days.",
        questions = listOf(
            q("我能够笑，并能看到事情有趣的一面", "I have been able to laugh and see the funny side of things", listOf(o("和以前一样", "As much as I always could", 0), o("现在没有以前那么多", "Not quite so much now", 1), o("现在肯定少了很多", "Definitely not so much now", 2), o("完全不能", "Not at all", 3))),
            q("我对事情抱有期待和享受", "I have looked forward with enjoyment to things", listOf(o("和以前一样", "As much as I ever did", 0), o("比以前少一些", "Rather less than I used to", 1), o("肯定比以前少", "Definitely less than I used to", 2), o("几乎没有", "Hardly at all", 3))),
            q("事情不顺时，我会不必要地责怪自己", "I have blamed myself unnecessarily when things went wrong", listOf(o("大多数时候会", "Yes, most of the time", 3), o("有时会", "Yes, some of the time", 2), o("很少会", "Not very often", 1), o("从来不会", "No, never", 0))),
            q("我会没有明显理由地焦虑或担心", "I have been anxious or worried for no good reason", listOf(o("完全没有", "No, not at all", 0), o("几乎没有", "Hardly ever", 1), o("有时会", "Yes, sometimes", 2), o("经常", "Yes, very often", 3))),
            q("我会没有明显理由地感到害怕或惊慌", "I have felt scared or panicky for no very good reason", listOf(o("经常", "Yes, quite a lot", 3), o("有时", "Yes, sometimes", 2), o("不太会", "No, not much", 1), o("完全不会", "No, not at all", 0))),
            q("事情压得我喘不过气", "Things have been getting on top of me", listOf(o("大多数时候完全应付不了", "Yes, most of the time I haven't been able to cope at all", 3), o("有时应付得不如平时好", "Yes, sometimes I haven't been coping as well as usual", 2), o("大多数时候应付得还好", "No, most of the time I have coped quite well", 1), o("和以前一样应付得很好", "No, I have been coping as well as ever", 0))),
            q("我因为不开心而难以入睡", "I have been so unhappy that I have had difficulty sleeping", listOf(o("大多数时候", "Yes, most of the time", 3), o("有时", "Yes, sometimes", 2), o("很少", "Not very often", 1), o("完全没有", "No, not at all", 0))),
            q("我感到悲伤或痛苦", "I have felt sad or miserable", listOf(o("大多数时候", "Yes, most of the time", 3), o("经常", "Yes, quite often", 2), o("不太经常", "Not very often", 1), o("完全没有", "No, not at all", 0))),
            q("我因为不开心而哭泣", "I have been so unhappy that I have been crying", listOf(o("大多数时候", "Yes, most of the time", 3), o("经常", "Yes, quite often", 2), o("偶尔", "Only occasionally", 1), o("从来没有", "No, never", 0))),
            q("我出现过伤害自己的念头", "The thought of harming myself has occurred to me", listOf(o("经常", "Yes, quite often", 3), o("有时", "Sometimes", 2), o("很少", "Hardly ever", 1), o("从来没有", "Never", 0)))
        ),
        bands = listOf(
            b(0, 9, "low", "较低", "Lower", "目前没有明显的围产期抑郁筛查信号。", "There is no clear perinatal depression signal at present."),
            b(10, 12, "possible", "可能存在", "Possible", "得分处于需要关注的区间。建议和医生或助产/产后服务人员讨论。", "Your score is in a range that warrants attention. Consider discussing it with a doctor or maternity service."),
            b(13, 30, "likely", "建议尽快评估", "Further assessment recommended", "得分达到 13 分及以上。建议尽快由专业人员评估，并告诉信任的人你的状态。", "A score of 13 or more suggests prompt assessment by a professional, and letting someone you trust know how you are doing.")
        ),
        estimatedMinutes = 3,
        featured = false,
        source = "EPDS: Cox, Holden & Sagovsky (1987). 使用请保留来源说明。",
        sourceEn = "EPDS: Cox, Holden & Sagovsky (1987). Keep the source attribution when using it.",
        note = "第 10 题涉及自伤念头。如果得分大于 0，请优先寻求帮助。",
        noteEn = "Item 10 asks about thoughts of self-harm. If you score above 0, prioritise getting help.",
        selfHarmQuestionIndex = 9
    )

    val dast10 = ScaleDef(
        type = "DAST10",
        name = "药物使用筛查（DAST-10）",
        nameEn = "Drug Abuse Screening Test (DAST-10)",
        shortName = "DAST-10",
        category = "物质使用",
        categoryEn = "Substance use",
        description = "10 项药物使用筛查，不包含酒精。",
        descriptionEn = "A 10-item screen for drug use, excluding alcohol.",
        timeframe = "过去 12 个月",
        timeframeEn = "Over the past 12 months",
        instruction = "请根据过去 12 个月使用药物（不含酒精）的情况回答。",
        instructionEn = "Answer based on your use of drugs other than alcohol over the past 12 months.",
        questions = listOf(
            q("你是否使用过非医疗原因的药物？", "Have you used drugs other than those required for medical reasons?", yesNo),
            q("你是否同时使用一种以上的药物？", "Do you abuse more than one drug at a time?", yesNo),
            q("你是否无法在想停用时停下来？", "Are you unable to stop using drugs when you want to?", yesNo),
            q("你是否因药物使用出现过黑蒙或闪回？", "Have you ever had blackouts or flashbacks as a result of drug use?", yesNo),
            q("你是否因药物使用感到难受或内疚？", "Do you ever feel bad or guilty about your drug use?", yesNo),
            q("你的伴侣或父母是否抱怨过你的药物使用？", "Does your spouse or parents ever complain about your involvement with drugs?", yesNo),
            q("你是否因药物使用忽略了家人？", "Have you neglected your family because of your use of drugs?", yesNo),
            q("你是否为了获得药物而参与违法活动？", "Have you engaged in illegal activities in order to obtain drugs?", yesNo),
            q("停药时你是否出现过戒断症状？", "Have you ever experienced withdrawal symptoms when you stopped taking drugs?", yesNo),
            q("你是否因药物使用出现身体或心理问题？", "Have you had medical problems as a result of your drug use?", yesNo)
        ),
        bands = listOf(
            b(0, 0, "none", "无明显问题", "No clear problem", "目前没有报告明显的药物使用问题。", "No clear drug-use problem is reported at present."),
            b(1, 2, "low", "低风险", "Low risk", "有一些风险信号。可以继续观察，并避免自行突然停药。", "There are some risk signals. Keep observing and avoid stopping any dependence-forming drug suddenly on your own."),
            b(3, 5, "moderate", "中等风险", "Moderate risk", "建议与专业人员讨论药物使用及身体、心理影响。", "Consider discussing drug use and its physical and psychological effects with a professional."),
            b(6, 10, "high", "较高风险", "Higher risk", "药物使用风险较高。建议尽快寻求专业帮助；如出现严重戒断或过量风险，请立即就医。", "Drug use is higher risk. Seek professional help soon; if there is severe withdrawal or overdose risk, seek emergency care.")
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "DAST-10: Skinner (1982) / NIDA. 公共领域常用版本。",
        sourceEn = "DAST-10: Skinner (1982) / NIDA. Commonly used public-domain version.",
        note = "本量表不评估酒精使用；酒精请使用 AUDIT-C。",
        noteEn = "This scale does not assess alcohol; use AUDIT-C for alcohol."
    )

    val dass21 = ScaleDef(
        type = "DASS21",
        name = "抑郁焦虑压力量表（DASS-21）",
        nameEn = "Depression, Anxiety and Stress Scale (DASS-21)",
        shortName = "DASS-21",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "21 项自评，分别了解抑郁、焦虑和压力。",
        descriptionEn = "A 21-item measure with separate depression, anxiety and stress scores.",
        timeframe = "过去一周",
        timeframeEn = "Over the past week",
        instruction = "请根据过去一周的情况，选择每个陈述适用于你的程度。",
        instructionEn = "Over the past week, choose how much each statement applied to you.",
        questions = listOf(
            q("我觉得很难让自己放松下来", "I found it hard to wind down", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我感到口干", "I was aware of dryness of my mouth", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我完全无法体验到积极情绪", "I couldn't seem to experience any positive feeling at all", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我感到呼吸困难", "I experienced breathing difficulty", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得很难主动开始做事", "I found it difficult to work up the initiative to do things", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我容易对事情反应过度", "I tended to over-react to situations", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我感到发抖", "I experienced trembling", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得自己消耗了很多神经能量", "I felt that I was using a lot of nervous energy", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我担心自己会在某些场合惊恐、出丑", "I was worried about situations in which I might panic and make a fool of myself", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得没有什么可期待的", "I felt that I had nothing to look forward to", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我发现自己很容易激动不安", "I found myself getting agitated", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得很难放松下来", "I found it difficult to relax", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我对阻碍我继续手头事情的一切感到不耐烦", "I was intolerant of anything that kept me from getting on", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得自己快要惊恐发作了", "I felt I was close to panic", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我感到情绪低落、忧郁", "I felt down-hearted and blue", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我无法对任何事情产生热情", "I was unable to become enthusiastic about anything", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得自己作为一个人没有多少价值", "I felt I wasn't worth much as a person", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得自己很容易被激怒", "I felt that I was rather touchy", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("在没有体力活动时，我也能感觉到心跳", "I was aware of the action of my heart in the absence of physical exertion", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我会没有明显理由地感到害怕", "I felt scared without any good reason", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3))),
            q("我觉得生活没有意义", "I felt that life was meaningless", listOf(o("完全不适用", "Did not apply to me at all", 0), o("有时适用", "Applied to me to some degree, or some of the time", 1), o("经常适用", "Applied to me to a considerable degree, or a good part of the time", 2), o("几乎总是适用", "Applied to me very much, or most of the time", 3)))
        ),
        bands = listOf(
            b(0, 9, "normal", "正常范围", "Normal", "三个分量表目前都在正常范围。继续记录变化即可。", "All three subscales are currently in the normal range. Keep tracking changes."),
            b(10, 19, "mild", "轻度", "Mild", "有轻度信号。可以关注睡眠、压力和支持系统，必要时与专业人员讨论。", "There are mild signals. Pay attention to sleep, stress and support, and discuss with a professional if needed."),
            b(20, 39, "moderate", "中度", "Moderate", "症状负担达到中度。建议把结果带给专业人员讨论。", "Symptom burden is moderate. Consider sharing this result with a professional."),
            b(40, 54, "severe", "重度", "Severe", "症状负担较重。建议尽快联系专业人员。", "Symptom burden is severe. Consider contacting a professional soon."),
            b(55, 63, "extremely_severe", "极重度", "Extremely severe", "症状负担很重。建议尽快寻求专业帮助；如出现自伤念头，请立即联系急救或危机热线。", "Symptom burden is extremely severe. Seek professional help soon; if you have thoughts of self-harm, contact emergency services or a crisis line immediately.")
        ),
        scoring = ScaleScoring.DASS21,
        maxScoreOverride = 63,
        estimatedMinutes = 5,
        featured = false,
        source = "DASS-21: Lovibond & Lovibond (1995). 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "DASS-21: Lovibond & Lovibond (1995). Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "结果会分别显示抑郁、焦虑、压力三个分量表。",
        noteEn = "The result shows separate depression, anxiety and stress subscale scores."
    )

    val psqi = ScaleDef(
        type = "PSQI",
        name = "匹兹堡睡眠质量指数（PSQI）",
        nameEn = "Pittsburgh Sleep Quality Index (PSQI)",
        shortName = "PSQI",
        category = "睡眠",
        categoryEn = "Sleep",
        description = "按标准七个成分计分的睡眠质量自评，了解过去一个月的睡眠。",
        descriptionEn = "A sleep quality measure scored with the standard seven components over the past month.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "以下问题关于过去一个月的睡眠习惯。请选择最接近的情况；上床和起床时间以小时区间近似。",
        instructionEn = "The following questions are about your sleep habits over the past month. Choose the closest answer; bedtime and wake time use one-hour ranges.",
        questions = listOf(
            q("你通常几点上床睡觉？", "What time have you usually gone to bed?", psqiBedtimeOptions),
            q("你通常需要多久才能入睡？", "How long has it usually taken you to fall asleep?", psqiLatencyOptions),
            q("你通常几点起床？", "What time have you usually gotten up?", psqiWakeOptions),
            q("你每晚实际睡眠多久？", "How many hours of actual sleep did you get at night?", psqiDurationOptions),
            q("过去一个月，你有多经常难以在 30 分钟内入睡？", "How often have you had trouble sleeping because you could not get to sleep within 30 minutes?", psqiFreqOptions),
            q("夜间或清晨醒来", "Wake up in the middle of the night or early morning", psqiFreqOptions),
            q("需要起床上厕所", "Have to get up to use the bathroom", psqiFreqOptions),
            q("呼吸不畅", "Cannot breathe comfortably", psqiFreqOptions),
            q("咳嗽或大声打鼾", "Cough or snore loudly", psqiFreqOptions),
            q("感到太冷", "Feel too cold", psqiFreqOptions),
            q("感到太热", "Feel too hot", psqiFreqOptions),
            q("做噩梦", "Have bad dreams", psqiFreqOptions),
            q("疼痛", "Have pain", psqiFreqOptions),
            q("其他影响睡眠的原因", "Other reasons for sleep disturbance", psqiFreqOptions),
            q("需要药物帮助入睡", "How often have you taken medicine to help you sleep?", psqiFreqOptions),
            q("白天难以保持清醒", "How often have you had trouble staying awake while driving, eating or socialising?", psqiFreqOptions),
            q("保持做事的热情有困难", "How much of a problem has it been to keep up enough enthusiasm to get things done?", listOf(o("没有困难", "No problem at all", 0), o("只有一点困难", "Only a very slight problem", 1), o("有些困难", "Somewhat of a problem", 2), o("困难很大", "A very big problem", 3))),
            q("整体睡眠质量", "Overall sleep quality", listOf(o("很好", "Very good", 0), o("较好", "Fairly good", 1), o("较差", "Fairly bad", 2), o("很差", "Very bad", 3)))
        ),
        bands = listOf(
            b(0, 5, "good", "睡眠质量较好", "Good sleep quality", "PSQI 总分较低，睡眠质量总体较好。", "The PSQI total is low, suggesting generally good sleep quality."),
            b(6, 10, "fair", "睡眠质量一般", "Fair sleep quality", "有一些睡眠问题。可以继续记录睡眠节律和影响因素。", "There are some sleep problems. Keep tracking your sleep rhythm and what affects it."),
            b(11, 16, "poor", "睡眠质量较差", "Poor sleep quality", "睡眠问题已经比较明显。建议与医生或睡眠专业人员讨论。", "Sleep problems are noticeable. Consider discussing them with a doctor or sleep specialist."),
            b(17, 21, "very_poor", "睡眠质量很差", "Very poor sleep quality", "睡眠负担较重。建议尽快寻求专业帮助。", "The sleep burden is heavy. Consider seeking professional help soon.")
        ),
        scoring = ScaleScoring.PSQI,
        maxScoreOverride = 21,
        estimatedMinutes = 5,
        featured = false,
        source = "PSQI: Buysse et al. (1989). 临床与研究常用；电子版再分发前请确认授权。",
        sourceEn = "PSQI: Buysse et al. (1989). Commonly used in clinical care and research; check permission before redistributing an electronic version.",
        note = "按标准 PSQI 七成分计分：主观质量、入睡潜伏期、睡眠时长、睡眠效率、睡眠紊乱、助眠药物、日间功能。上床/起床时间用小时区间近似，睡眠效率为估算值；结果仅用于记录与就诊沟通。",
        noteEn = "Scored with the standard seven PSQI components: subjective quality, latency, duration, efficiency, disturbance, medication and daytime dysfunction. Bedtime and wake time use one-hour ranges, so efficiency is approximate; use for tracking and clinical communication only."
    )

    val pss4 = ScaleDef(
        type = "PSS4",
        name = "知觉压力量表（PSS-4）",
        nameEn = "Perceived Stress Scale (PSS-4)",
        shortName = "PSS-4",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "4 项自评，了解过去一个月的压力感受。",
        descriptionEn = "A 4-item measure of perceived stress over the past month.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "请选择过去一个月最符合你感受的频率。",
        instructionEn = "Choose the frequency that best describes your feelings over the past month.",
        questions = listOf(
            q("你有多经常感到无法控制生活中重要的事情？", "How often have you felt that you were unable to control the important things in your life?", listOf(o("从不", "Never", 0), o("几乎从不", "Almost never", 1), o("有时", "Sometimes", 2), o("相当经常", "Fairly often", 3), o("非常经常", "Very often", 4))),
            q("你有多经常对自己处理个人问题的能力感到有信心？", "How often have you felt confident about your ability to handle your personal problems?", listOf(o("非常经常", "Very often", 0), o("相当经常", "Fairly often", 1), o("有时", "Sometimes", 2), o("几乎从不", "Almost never", 3), o("从不", "Never", 4))),
            q("你有多经常觉得事情正按你的意愿发展？", "How often have you felt that things were going your way?", listOf(o("非常经常", "Very often", 0), o("相当经常", "Fairly often", 1), o("有时", "Sometimes", 2), o("几乎从不", "Almost never", 3), o("从不", "Never", 4))),
            q("你有多经常觉得困难堆积得太高，无法克服？", "How often have you felt difficulties were piling up so high that you could not overcome them?", listOf(o("从不", "Never", 0), o("几乎从不", "Almost never", 1), o("有时", "Sometimes", 2), o("相当经常", "Fairly often", 3), o("非常经常", "Very often", 4)))
        ),
        bands = listOf(
            b(0, 5, "low", "压力较低", "Lower stress", "目前的压力感受较低。", "Your current perceived stress appears lower."),
            b(6, 9, "moderate", "中等压力", "Moderate stress", "压力处于中等水平。可以留意睡眠、休息和支持系统。", "Stress is moderate. Pay attention to sleep, rest and support."),
            b(10, 16, "high", "压力较高", "Higher stress", "压力感受较高。建议给自己安排恢复时间，并与信任的人或专业人员聊聊。", "Perceived stress is higher. Build in recovery time and talk with someone you trust or a professional.")
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "PSS-4: Cohen, Kamarck & Mermelstein (1983). 非商业使用请保留来源。",
        sourceEn = "PSS-4: Cohen, Kamarck & Mermelstein (1983). Keep the source attribution for non-commercial use.",
        note = "PSS-4 测量压力感受，不是焦虑或抑郁的诊断。",
        noteEn = "The PSS-4 measures perceived stress and is not a diagnosis of anxiety or depression."
    )

    val rosenberg = ScaleDef(
        type = "ROSENBERG",
        name = "自尊量表（Rosenberg）",
        nameEn = "Rosenberg Self-Esteem Scale",
        shortName = "Rosenberg",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "10 项自评，了解整体自我价值感。",
        descriptionEn = "A 10-item measure of global self-worth.",
        timeframe = "总体感受",
        timeframeEn = "How you generally feel",
        instruction = "请根据你对自己的总体感受选择同意程度。",
        instructionEn = "Choose how much you agree based on how you generally feel about yourself.",
        questions = listOf(
            q("总体而言，我对自己感到满意", "On the whole, I am satisfied with myself", agreeScale),
            q("有时我觉得自己一无是处", "At times I think I am no good at all", agreeScaleReversed),
            q("我觉得自己有一些优点", "I feel that I have a number of good qualities", agreeScale),
            q("我能够像大多数人一样把事情做好", "I am able to do things as well as most other people", agreeScale),
            q("我觉得自己没有什么值得骄傲的", "I feel I do not have much to be proud of", agreeScaleReversed),
            q("我有时确实觉得自己很没用", "I certainly feel useless at times", agreeScaleReversed),
            q("我觉得自己是一个有价值的人，至少和别人一样", "I feel that I'm a person of worth, at least on an equal plane with others", agreeScale),
            q("我希望自己能更尊重自己一些", "I wish I could have more respect for myself", agreeScaleReversed),
            q("总的来说，我倾向于觉得自己是个失败者", "All in all, I am inclined to feel that I am a failure", agreeScaleReversed),
            q("我对自己持积极的态度", "I take a positive attitude toward myself", agreeScale)
        ),
        bands = listOf(
            b(0, 14, "low", "自尊偏低", "Lower self-esteem", "目前的自我价值感偏低。可以留意自我批评的声音，并在需要时与信任的人或专业人员聊聊。", "Current self-worth appears lower. Notice self-critical thoughts and talk with someone you trust or a professional if needed."),
            b(15, 25, "average", "一般范围", "Average range", "自尊处于一般范围。", "Self-esteem is in the average range."),
            b(26, 30, "high", "自尊较高", "Higher self-esteem", "目前的自我价值感较高。", "Current self-worth appears higher.")
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "Rosenberg Self-Esteem Scale: Morris Rosenberg (1965). 广泛用于研究与临床。",
        sourceEn = "Rosenberg Self-Esteem Scale: Morris Rosenberg (1965). Widely used in research and clinical settings.",
        note = "自尊分数受教育、文化、处境等影响，不作为诊断。",
        noteEn = "Self-esteem scores are influenced by education, culture and circumstances; this is not a diagnosis."
    )

    val aq10 = ScaleDef(
        type = "AQ10",
        name = "孤独症谱系筛查（AQ-10）",
        nameEn = "Autism Spectrum Quotient – 10 items (AQ-10)",
        shortName = "AQ-10",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "10 项成人孤独症谱系快速筛查。",
        descriptionEn = "A 10-item brief screen for autism spectrum traits in adults.",
        timeframe = "总体情况",
        timeframeEn = "How you generally are",
        instruction = "请根据你通常的情况选择同意程度。",
        instructionEn = "Choose how much you agree based on how you usually are.",
        questions = listOf(
            q("我经常注意到别人没有注意到的小声音", "I often notice small sounds when others do not", aqAgree),
            q("我通常更关注整体画面，而不是细节", "I usually concentrate more on the whole picture, rather than the small details", aqDisagree),
            q("我很容易同时做几件事", "I find it easy to do more than one thing at once", aqDisagree),
            q("如果有打断，我能很快回到刚才在做的事", "If there is an interruption, I can switch back to what I was doing very quickly", aqDisagree),
            q("别人说话时，我很容易听出言外之意", "I find it easy to read between the lines when someone is talking to me", aqDisagree),
            q("我知道如何判断听我说话的人是否感到无聊", "I know how to tell if someone listening to me is getting bored", aqDisagree),
            q("读故事时，我很难理解角色的意图", "When I'm reading a story, I find it difficult to work out the characters' intentions", aqAgree),
            q("我喜欢收集某一类事物的信息", "I like to collect information about categories of things", aqAgree),
            q("我很容易通过看脸判断别人在想什么或感受什么", "I find it easy to work out what someone is thinking or feeling just by looking at their face", aqDisagree),
            q("我很难判断别人的意图", "I find it difficult to work out people's intentions", aqAgree)
        ),
        bands = listOf(
            b(0, 5, "below", "低于筛查界值", "Below screening threshold", "得分低于 AQ-10 常用筛查界值（6 分）。", "Your score is below the common AQ-10 screening threshold of 6."),
            b(6, 10, "positive", "建议进一步评估", "Further assessment suggested", "得分达到 6 分及以上。建议由专业人员进一步评估；这不是孤独症诊断。", "A score of 6 or more suggests further assessment by a professional. This is not a diagnosis of autism.")
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "AQ-10: Allison, Auyeung & Baron-Cohen (2012). 研究与临床使用请保留来源。",
        sourceEn = "AQ-10: Allison, Auyeung & Baron-Cohen (2012). Keep the source attribution for research and clinical use.",
        note = "AQ-10 不是孤独症诊断工具。",
        noteEn = "The AQ-10 is not a diagnostic tool for autism."
    )


    // ---------- 0.4.2 新增量表 ----------

    private val pssOpt = listOf(o("从不", "Never", 0), o("几乎从不", "Almost never", 1), o("有时", "Sometimes", 2), o("相当频繁", "Fairly often", 3), o("非常频繁", "Very often", 4))

    private val pssRev = listOf(o("从不", "Never", 4), o("几乎从不", "Almost never", 3), o("有时", "Sometimes", 2), o("相当频繁", "Fairly often", 1), o("非常频繁", "Very often", 0))


    val pss10 = ScaleDef(
        type = "PSS10",
        name = "知觉压力量表（PSS-10）",
        nameEn = "Perceived Stress Scale (PSS-10)",
        shortName = "PSS-10",
        category = "压力与适应",
        categoryEn = "Stress & coping",
        description = "10 项压力自评，了解过去一个月里生活被感知为不可预测、不可控制的程度。",
        descriptionEn = "A 10-item measure of how unpredictable, uncontrollable and overloaded life has felt in the past month.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "请回想过去一个月，以下情况出现的频率，按第一直觉作答。",
        instructionEn = "Think about the last month and how often each statement applied. Answer with your first instinct.",
        questions = listOf(
            q("因为发生意外的事情而感到心烦意乱", "Been upset because of something that happened unexpectedly", pssOpt),
            q("感到无法控制生活中重要的事情", "Felt that you were unable to control the important things in your life", pssOpt),
            q("感到紧张不安或压力很大", "Felt nervous and stressed", pssOpt),
            q("对自己处理个人问题的能力感到自信", "Felt confident about your ability to handle your personal problems", pssRev),
            q("感到事情正按你的意愿发展", "Felt that things were going your way", pssRev),
            q("发现你无法应对所有必须做的事情", "Found that you could not cope with all the things that you had to do", pssOpt),
            q("能够控制生活中令你恼火的事情", "Been able to control irritations in your life", pssRev),
            q("感到自己能掌控事情", "Felt that you were on top of things", pssRev),
            q("因为一些无法控制的事情而生气", "Been angered because of things that were outside of your control", pssOpt),
            q("感到困难堆积太多，无法克服", "Felt difficulties were piling up so high that you could not overcome them", pssOpt),
        ),
        bands = listOf(
            b(0, 13, "low", "压力较低", "Low stress", "目前感知到的压力较低。保持规律作息和现有节奏即可。", "Your perceived stress is fairly low. Keep your current routines and sleep pattern."),
            b(14, 26, "moderate", "中等压力", "Moderate stress", "压力水平中等。可以看看哪些事情可以放下或分担，给睡眠和活动留出空间。", "Stress is moderate. See what can be dropped or shared, and protect time for sleep and activity."),
            b(27, 40, "high", "高压力", "High stress", "近期压力较高。建议和医生或心理咨询师讨论，并优先保证休息和支持。", "Recent stress is high. Consider talking to a doctor or counsellor, and prioritise rest and support."),
        ),
        estimatedMinutes = 3,
        featured = false,
        source = "PSS-10: Cohen, Kamarck & Mermelstein (1983). 研究/教育用途可免费使用，商业使用请先确认授权。",
        sourceEn = "PSS-10: Cohen, Kamarck & Mermelstein (1983). Free for research and education; check licensing for commercial use.",
        note = "压力分数不是诊断，也不能替代专业评估。",
        noteEn = "A stress score is not a diagnosis and does not replace professional assessment.",
    )



    val stopbang = ScaleDef(
        type = "STOPBANG",
        name = "睡眠呼吸暂停风险（STOP-BANG）",
        nameEn = "STOP-BANG Sleep Apnea Screen",
        shortName = "STOP-BANG",
        category = "睡眠",
        categoryEn = "Sleep",
        description = "8 项睡眠呼吸暂停风险筛查，常用于打鼾、白天嗜睡或睡眠质量差的人。",
        descriptionEn = "An 8-item screen for obstructive sleep apnea risk, often used with snoring or daytime sleepiness.",
        timeframe = "过去一段时间",
        timeframeEn = "Recent months",
        instruction = "请根据自己近来的情况回答是或否。",
        instructionEn = "Answer yes or no based on your recent situation.",
        questions = listOf(
            q("打鼾：你是否大声打鼾（比说话声大，或关上门也能听到）", "Snoring: do you snore loudly (louder than talking, or heard through a closed door)", yesNo),
            q("疲劳：白天是否经常感到疲倦、乏力或嗜睡", "Tired: do you often feel tired, fatigued or sleepy during the day", yesNo),
            q("观察到呼吸停止：是否有人看到你睡眠中呼吸暂停或窒息", "Observed: has anyone seen you stop breathing during sleep", yesNo),
            q("血压：是否有高血压，或正在接受高血压治疗", "Pressure: do you have or are you treated for high blood pressure", yesNo),
            q("BMI 是否大于 35", "Is your BMI greater than 35", yesNo),
            q("年龄是否大于 50 岁", "Is your age greater than 50", yesNo),
            q("颈围是否大于 40 厘米", "Is your neck circumference greater than 40 cm", yesNo),
            q("性别是否为男性", "Is your gender male", yesNo),
        ),
        bands = listOf(
            b(0, 2, "low", "低风险", "Low risk", "目前筛查风险较低。如果仍然打鼾或白天嗜睡，可以和医生讨论是否需要进一步检查。", "Screening risk is low. If snoring or daytime sleepiness persists, discuss further testing with a doctor."),
            b(3, 4, "intermediate", "中等风险", "Intermediate risk", "存在中等风险。建议和医生讨论睡眠监测，尤其是合并高血压或白天嗜睡时。", "Intermediate risk. Consider discussing a sleep study with a doctor, especially with high blood pressure or daytime sleepiness."),
            b(5, 8, "high", "高风险", "High risk", "筛查提示较高风险。建议尽快就医评估睡眠呼吸暂停；这不是诊断。", "The screen suggests high risk. See a doctor for assessment; this is not a diagnosis."),
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "STOP-BANG: Chung et al. (2008). 可用于筛查的免费工具。",
        sourceEn = "STOP-BANG: Chung et al. (2008). A freely available screening tool.",
        note = "STOP-BANG 只用于风险筛查，确诊需要睡眠监测。",
        noteEn = "STOP-BANG screens risk only; diagnosis needs a sleep study.",
    )

    private val auditFreq = listOf(o("从不", "Never", 0), o("每月不到一次", "Less than monthly", 1), o("每月 2-4 次", "2-4 times a month", 2), o("每周 2-3 次", "2-3 times a week", 3), o("每周 4 次以上", "4 or more times a week", 4))

    private val auditAmount = listOf(o("1-2 杯", "1 or 2", 0), o("3-4 杯", "3 or 4", 1), o("5-6 杯", "5 or 6", 2), o("7-9 杯", "7 to 9", 3), o("10 杯以上", "10 or more", 4))

    private val auditSix = listOf(o("从不", "Never", 0), o("每月不到一次", "Less than monthly", 1), o("每月一次", "Monthly", 2), o("每周一次", "Weekly", 3), o("每天或几乎每天", "Daily or almost daily", 4))

    private val auditHarm = listOf(o("没有", "No", 0), o("有，但不在过去一年", "Yes, but not in the past year", 2), o("有，且在过去一年", "Yes, during the past year", 4))


    val audit = ScaleDef(
        type = "AUDIT",
        name = "酒精使用障碍筛查（AUDIT）",
        nameEn = "Alcohol Use Disorders Identification Test (AUDIT)",
        shortName = "AUDIT",
        category = "物质使用",
        categoryEn = "Substance use",
        description = "WHO 的 10 项酒精使用筛查，覆盖饮酒量、依赖信号和酒精相关问题。",
        descriptionEn = "The WHO 10-item alcohol screen covering consumption, dependence signs and alcohol-related problems.",
        timeframe = "过去一年",
        timeframeEn = "Over the past year",
        instruction = "请按过去一年的实际情况作答。一杯约含 10 克纯酒精。",
        instructionEn = "Answer for the past year. One drink is about 10 g of pure alcohol.",
        questions = listOf(
            q("你多久喝一次含酒精的饮料", "How often do you have a drink containing alcohol", auditFreq),
            q("在喝酒的日子里，你通常喝几杯", "How many drinks do you have on a typical day when you are drinking", auditAmount),
            q("你多久有一次喝到六杯或以上", "How often do you have six or more drinks on one occasion", auditSix),
            q("过去一年里，有多少次你一旦开始喝酒就难以停下", "How often during the past year have you found that you were not able to stop drinking once you had started", auditSix),
            q("过去一年里，有多少次因为喝酒而没能做你应该做的事", "How often during the past year have you failed to do what was normally expected of you because of drinking", auditSix),
            q("过去一年里，有多少次你早上需要喝酒才能开始一天", "How often during the past year have you needed a drink in the morning to get yourself going", auditSix),
            q("过去一年里，有多少次喝酒后感到内疚或后悔", "How often during the past year have you had a feeling of guilt or remorse after drinking", auditSix),
            q("过去一年里，有多少次因为喝酒而记不清前一晚发生的事", "How often during the past year have you been unable to remember what happened the night before because of your drinking", auditSix),
            q("你是否因为喝酒而让自己或他人受伤", "Have you or someone else been injured because of your drinking", auditHarm),
            q("是否有亲友、医生或其他医务人员建议你减少饮酒", "Has a relative, friend, doctor or other health worker been concerned about your drinking or suggested you cut down", auditHarm),
        ),
        bands = listOf(
            b(0, 7, "low", "低风险", "Low risk", "目前酒精使用风险较低。继续保持。", "Current alcohol use appears low risk. Keep it that way."),
            b(8, 15, "hazardous", "有害使用", "Hazardous use", "已出现有害使用的信号。建议减少饮酒，并和医生讨论。", "Signs of hazardous use. Consider cutting down and discussing this with a doctor."),
            b(16, 19, "harmful", "高危害使用", "Harmful use", "酒精相关问题较明显，建议尽快寻求专业评估。", "Alcohol-related problems are significant. Seek a professional assessment soon."),
            b(20, 40, "dependence", "可能存在依赖", "Possible dependence", "筛查提示可能存在酒精依赖。请尽快就医，突然停酒可能有风险。", "The screen suggests possible dependence. See a doctor soon; stopping suddenly can be dangerous."),
        ),
        estimatedMinutes = 3,
        featured = false,
        source = "AUDIT: WHO (Babor et al., 2001). 可免费使用的公共卫生筛查工具。",
        sourceEn = "AUDIT: WHO (Babor et al., 2001). A freely available public-health screening tool.",
        note = "AUDIT 不是诊断；如存在依赖，请勿自行突然停酒。",
        noteEn = "AUDIT is not a diagnosis; do not stop alcohol suddenly if dependence is possible.",
    )

    private val ftndFirst = listOf(o("超过 60 分钟", "More than 60 minutes", 0), o("31-60 分钟", "31-60 minutes", 1), o("6-30 分钟", "6-30 minutes", 2), o("5 分钟以内", "Within 5 minutes", 3))

    private val ftndYesNo = listOf(o("否", "No", 0), o("是", "Yes", 1))

    private val ftndWhich = listOf(o("早晨第一支", "The first one in the morning", 0), o("其他任何一支", "Any other", 1))

    private val ftndCount = listOf(o("10 支或更少", "10 or fewer", 0), o("11-20 支", "11-20", 1), o("21-30 支", "21-30", 2), o("31 支以上", "31 or more", 3))


    val ftnd = ScaleDef(
        type = "FTND",
        name = "尼古丁依赖测试（FTND）",
        nameEn = "Fagerstrom Test for Nicotine Dependence (FTND)",
        shortName = "FTND",
        category = "物质使用",
        categoryEn = "Substance use",
        description = "6 项尼古丁依赖自评，常用于了解戒烟难度和依赖程度。",
        descriptionEn = "A 6-item measure of nicotine dependence, often used to gauge difficulty quitting.",
        timeframe = "目前",
        timeframeEn = "Currently",
        instruction = "请根据目前的吸烟情况作答（不吸烟可不做）。",
        instructionEn = "Answer for your current smoking (skip if you do not smoke).",
        questions = listOf(
            q("你早上醒来后多久抽第一支烟", "How soon after you wake up do you smoke your first cigarette", ftndFirst),
            q("在禁烟场所（如图书馆、医院）是否难以忍住不吸烟", "Do you find it difficult to refrain from smoking in places where it is forbidden", ftndYesNo),
            q("一天中哪一支烟最难放弃", "Which cigarette would you hate most to give up", ftndWhich),
            q("你每天抽多少支烟", "How many cigarettes per day do you smoke", ftndCount),
            q("醒后第一小时内吸烟是否比其他时间更频繁", "Do you smoke more frequently during the first hours after waking than during the rest of the day", ftndYesNo),
            q("即使生病卧床，你是否仍然吸烟", "Do you smoke even if you are so ill that you are in bed most of the day", ftndYesNo),
        ),
        bands = listOf(
            b(0, 2, "very_low", "极低依赖", "Very low dependence", "尼古丁依赖程度很低。戒烟通常相对容易，但也可以寻求支持。", "Nicotine dependence is very low. Quitting is usually easier, though support still helps."),
            b(3, 4, "low", "低依赖", "Low dependence", "依赖程度较低。设定戒烟日并清理烟具会有帮助。", "Low dependence. Setting a quit date and removing smoking cues can help."),
            b(5, 7, "moderate", "中等依赖", "Moderate dependence", "存在中等依赖。建议使用戒烟门诊或药物辅助，成功率更高。", "Moderate dependence. A quit clinic or medication support improves success."),
            b(8, 10, "high", "高依赖", "High dependence", "依赖程度较高。建议在医生指导下制定戒烟方案，必要时使用尼古丁替代。", "High dependence. Plan quitting with a doctor and consider nicotine replacement."),
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "FTND: Heatherton et al. (1991). 研究/临床常用，请保留来源。",
        sourceEn = "FTND: Heatherton et al. (1991). Commonly used in research and clinical settings; keep attribution.",
        note = "FTND 只评估依赖程度，不是诊断。",
        noteEn = "FTND measures dependence only; it is not a diagnosis.",
    )

    private val ucla = listOf(o("很少", "Hardly ever", 1), o("有时", "Some of the time", 2), o("经常", "Often", 3))


    val ucla3 = ScaleDef(
        type = "UCLA3",
        name = "孤独感量表（UCLA-3）",
        nameEn = "UCLA 3-Item Loneliness Scale",
        shortName = "UCLA-3",
        category = "社会与功能",
        categoryEn = "Social & functioning",
        description = "3 项孤独感简表，了解你感到缺少陪伴、被冷落或与他人隔离的频率。",
        descriptionEn = "A 3-item loneliness measure asking how often you feel you lack companionship, feel left out or isolated.",
        timeframe = "最近",
        timeframeEn = "Recently",
        instruction = "请按最近一段时间的真实感受作答。",
        instructionEn = "Answer based on how you have felt recently.",
        questions = listOf(
            q("你多久感到缺少陪伴", "How often do you feel that you lack companionship", ucla),
            q("你多久感到被冷落", "How often do you feel left out", ucla),
            q("你多久感到与他人隔离", "How often do you feel isolated from others", ucla),
        ),
        bands = listOf(
            b(3, 5, "low", "孤独感较低", "Low loneliness", "目前孤独感较低。继续保持与他人的联系。", "Loneliness is currently low. Keep up your connections with others."),
            b(6, 7, "moderate", "中等孤独感", "Moderate loneliness", "有一些孤独感。可以试着主动联系一位信任的人，或参加一个固定的小活动。", "Some loneliness. Try reaching out to someone you trust, or joining a small regular activity."),
            b(8, 9, "high", "较高孤独感", "High loneliness", "孤独感较明显。长期孤独会影响身心状态，建议主动建立支持，必要时寻求心理咨询。", "Loneliness is noticeable. It affects health over time; build support and consider counselling if it persists."),
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "UCLA-3: Hughes et al. (2004). 可免费用于研究和临床。",
        sourceEn = "UCLA-3: Hughes et al. (2004). Freely available for research and clinical use.",
        note = "孤独感不是诊断，但值得被认真对待。",
        noteEn = "Loneliness is not a diagnosis, but it deserves attention.",
    )

    private val wsasOpt = listOf(o("完全没有", "Not at all", 0), o("轻微", "Slightly", 1), o("明显", "Definitely", 2), o("中度", "Moderately", 3), o("较大", "Markedly", 4), o("很大", "Severely", 5), o("严重", "Very severely", 6), o("非常严重", "Extremely severe", 7), o("极严重", "Totally", 8))


    val wsas = ScaleDef(
        type = "WSAS",
        name = "工作与社会适应量表（WSAS）",
        nameEn = "Work and Social Adjustment Scale (WSAS)",
        shortName = "WSAS",
        category = "社会与功能",
        categoryEn = "Social & functioning",
        description = "5 项功能损害自评，了解症状对工作、家务、社交和关系的影响。",
        descriptionEn = "A 5-item measure of how much symptoms interfere with work, home, social and relationship functioning.",
        timeframe = "目前",
        timeframeEn = "Currently",
        instruction = "请评估你目前的症状对以下各方面的影响程度。",
        instructionEn = "Rate how much your current symptoms interfere with each area.",
        questions = listOf(
            q("工作或学习能力", "Your ability to work or study", wsasOpt),
            q("家务与家庭事务", "Home management and household chores", wsasOpt),
            q("社交与休闲活动", "Social and leisure activities", wsasOpt),
            q("与家人或朋友的关系", "Relationships with family or friends", wsasOpt),
            q("独自进行有意义活动的能力", "Your ability to do meaningful activities alone", wsasOpt),
        ),
        bands = listOf(
            b(0, 9, "subclinical", "功能影响轻微", "Minimal impairment", "目前功能受影响较小。", "Your functioning is currently affected only minimally."),
            b(10, 20, "moderate", "功能中度受损", "Moderate impairment", "症状已经开始影响日常功能。建议和医生或咨询师讨论。", "Symptoms are affecting daily functioning. Consider discussing this with a doctor or counsellor."),
            b(21, 40, "severe", "功能严重受损", "Severe impairment", "功能受损明显。建议尽快寻求专业帮助，并告诉信任的人。", "Functioning is significantly impaired. Seek professional help soon and tell someone you trust."),
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "WSAS: Mundt et al. (2002). 可免费用于研究和临床。",
        sourceEn = "WSAS: Mundt et al. (2002). Freely available for research and clinical use.",
        note = "WSAS 衡量功能影响，不用于诊断。",
        noteEn = "WSAS measures functional impact; it is not diagnostic.",
    )

    private val swlsOpt = listOf(o("非常不同意", "Strongly disagree", 1), o("不同意", "Disagree", 2), o("有点不同意", "Slightly disagree", 3), o("中立", "Neither agree nor disagree", 4), o("有点同意", "Slightly agree", 5), o("同意", "Agree", 6), o("非常同意", "Strongly agree", 7))


    val swls = ScaleDef(
        type = "SWLS",
        name = "生活满意度量表（SWLS）",
        nameEn = "Satisfaction with Life Scale (SWLS)",
        shortName = "SWLS",
        category = "社会与功能",
        categoryEn = "Social & functioning",
        description = "5 项整体生活满意度自评，了解你对自己生活的总体评价。",
        descriptionEn = "A 5-item measure of overall satisfaction with your life as a whole.",
        timeframe = "整体",
        timeframeEn = "Overall",
        instruction = "请按你对生活的整体感受作答。",
        instructionEn = "Answer based on your overall sense of your life.",
        questions = listOf(
            q("我的生活在大多数方面接近我的理想", "In most ways my life is close to my ideal", swlsOpt),
            q("我的生活条件很好", "The conditions of my life are excellent", swlsOpt),
            q("我对我的生活满意", "I am satisfied with my life", swlsOpt),
            q("到目前为止，我得到了生活中想要的重要东西", "So far I have gotten the important things I want in life", swlsOpt),
            q("如果重来一次，我几乎不会改变什么", "If I could live my life over, I would change almost nothing", swlsOpt),
        ),
        bands = listOf(
            b(5, 9, "extremely_dissatisfied", "非常不满意", "Extremely dissatisfied", "对目前生活非常不满意。这种感受值得被认真对待，建议和信任的人或专业人士聊聊。", "Very dissatisfied with life right now. This deserves attention; talk with someone you trust or a professional."),
            b(10, 14, "dissatisfied", "不满意", "Dissatisfied", "对生活不太满意。可以试着找出一个小的、可改变的部分。", "Dissatisfied with life. Try identifying one small area that could change."),
            b(15, 19, "slightly_dissatisfied", "略不满意", "Slightly dissatisfied", "整体略不满意。留意睡眠、关系和日常节奏。", "Slightly dissatisfied overall. Pay attention to sleep, relationships and daily rhythm."),
            b(20, 20, "neutral", "中性", "Neutral", "对生活感受中性，既不满意也不不满意。", "A neutral evaluation of life."),
            b(21, 25, "slightly_satisfied", "略满意", "Slightly satisfied", "整体略满意。继续保持让你有掌控感的事情。", "Slightly satisfied. Keep doing what gives you a sense of control."),
            b(26, 30, "satisfied", "满意", "Satisfied", "对生活整体满意。", "You are satisfied with your life overall."),
            b(31, 35, "extremely_satisfied", "非常满意", "Extremely satisfied", "对生活非常满意。", "You are extremely satisfied with your life."),
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "SWLS: Diener et al. (1985). 可免费用于研究和临床。",
        sourceEn = "SWLS: Diener et al. (1985). Freely available for research and clinical use.",
        note = "生活满意度没有好坏标准，只是自我观察。",
        noteEn = "Life satisfaction has no right or wrong value; it is for self-observation.",
    )

    private val k10Opt = listOf(o("几乎没有", "None of the time", 1), o("偶尔", "A little of the time", 2), o("有时", "Some of the time", 3), o("大部分时间", "Most of the time", 4), o("所有时间", "All of the time", 5))


    val k10 = ScaleDef(
        type = "K10",
        name = "心理困扰量表（K10）",
        nameEn = "Kessler Psychological Distress Scale (K10)",
        shortName = "K10",
        category = "心理困扰",
        categoryEn = "Psychological distress",
        description = "10 项心理困扰自评，适用于过去 30 天的焦虑与抑郁相关困扰。",
        descriptionEn = "A 10-item measure of psychological distress over the past 30 days.",
        timeframe = "过去 30 天",
        timeframeEn = "Over the past 30 days",
        instruction = "在过去 30 天里，你多久有以下感受？",
        instructionEn = "In the past 30 days, how often did you feel the following?",
        questions = listOf(
            q("感到疲劳，即使没有做体力活", "Tired out for no good reason", k10Opt),
            q("感到紧张", "Nervous", k10Opt),
            q("感到太紧张而无法平静下来", "So nervous that nothing could calm you down", k10Opt),
            q("感到绝望", "Hopeless", k10Opt),
            q("感到不安或坐立不安", "Restless or fidgety", k10Opt),
            q("感到坐立不安，难以静坐", "So restless you could not sit still", k10Opt),
            q("感到情绪低落", "Depressed", k10Opt),
            q("感到做每件事都很费力", "That everything was an effort", k10Opt),
            q("感到自己毫无价值", "Worthless", k10Opt),
            q("感到害怕", "So sad that nothing could cheer you up", k10Opt),
        ),
        bands = listOf(
            b(10, 19, "low", "困扰较低", "Low distress", "目前心理困扰较低。", "Psychological distress is currently low."),
            b(20, 24, "mild", "轻度困扰", "Mild distress", "有一些心理困扰。留意睡眠与压力来源。", "Some psychological distress. Watch sleep and sources of stress."),
            b(25, 29, "moderate", "中度困扰", "Moderate distress", "心理困扰较明显。建议和医生或心理咨询师讨论。", "Noticeable distress. Consider discussing this with a doctor or counsellor."),
            b(30, 50, "severe", "重度困扰", "Severe distress", "心理困扰较重。建议尽快寻求专业帮助，并告诉信任的人。", "High distress. Seek professional help soon and tell someone you trust."),
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "K10: Kessler et al. (2002); WHO. 可用于非商业研究与公共卫生筛查。",
        sourceEn = "K10: Kessler et al. (2002); WHO. Available for non-commercial research and public-health screening.",
        note = "K10 不是诊断，分数高也不等于患病。",
        noteEn = "K10 is not a diagnosis; a high score does not mean you have a disorder.",
    )

    private val eat = listOf(o("总是", "Always", 3), o("经常", "Usually", 3), o("常常", "Often", 2), o("有时", "Sometimes", 1), o("很少", "Rarely", 0), o("从不", "Never", 0))


    val eat26 = ScaleDef(
        type = "EAT26",
        name = "进食态度量表（EAT-26）",
        nameEn = "Eating Attitudes Test (EAT-26)",
        shortName = "EAT-26",
        category = "进食",
        categoryEn = "Eating",
        description = "26 项进食态度与行为筛查，用于了解是否存在需要进一步评估的进食问题。",
        descriptionEn = "A 26-item screen of eating attitudes and behaviours that may warrant further assessment.",
        timeframe = "最近",
        timeframeEn = "Recently",
        instruction = "请按最近一段时间的实际情况作答。",
        instructionEn = "Answer based on your recent experience.",
        questions = listOf(
            q("我非常害怕变胖", "I am terrified about being overweight", eat),
            q("即使饿了，我也避免进食", "I avoid eating when I am hungry", eat),
            q("我全神贯注于食物", "I find myself preoccupied with food", eat),
            q("我无法控制地暴食", "I have gone on eating binges where I feel that I may not be able to stop", eat),
            q("我把食物切成小块", "I cut my food into small pieces", eat),
            q("我知道自己的进食方式让别人不安", "I am aware that others would prefer me to eat more", eat),
            q("我进食后感到内疚", "I feel guilty after eating", eat),
            q("我进食后感到腹胀", "I feel bloated after eating", eat),
            q("我尝试通过呕吐来控制体重", "I vomit after I have eaten", eat),
            q("我使用泻药或利尿剂来控制体重", "I take laxatives or diuretics to control my weight", eat),
            q("我剧烈运动来控制体重", "I exercise strenuously to burn off calories", eat),
            q("我的体重下降明显", "My weight has dropped significantly", eat),
            q("我担心自己太瘦", "I am worried about being too thin", eat),
            q("我对自己身体某些部位太胖感到困扰", "I am preoccupied with the desire to be thinner", eat),
            q("我比别人更慢地进食", "I take longer than others to eat my meals", eat),
            q("我避免吃含糖食物", "I avoid foods with sugar in them", eat),
            q("我吃减肥食品", "I eat diet foods", eat),
            q("我觉得食物控制了我的生活", "I feel that food controls my life", eat),
            q("我对食物有自我控制", "I display self-control around food", eat),
            q("我觉得别人逼我多吃", "I feel that others pressure me to eat", eat),
            q("我花太多时间和食物打交道", "I give too much time and thought to food", eat),
            q("我吃东西后感到不舒服", "I feel uncomfortable after eating sweets", eat),
            q("我想到节食", "I engage in dieting behaviour", eat),
            q("我喜欢胃里空空的感觉", "I like my stomach to be empty", eat),
            q("我喜欢尝试新食物", "I enjoy trying new rich foods", eat),
            q("我饭后有呕吐冲动", "I have the impulse to vomit after meals", eat),
        ),
        bands = listOf(
            b(0, 19, "low", "低于常见界值", "Below common threshold", "目前得分低于常用筛查界值（20 分）。如仍有进食困扰，可以直接和专业人士讨论。", "Your score is below the common threshold of 20. If eating still troubles you, talk with a professional."),
            b(20, 78, "risk", "达到筛查界值", "At or above threshold", "得分达到常用筛查界值（20 分）。建议由专业人员进一步评估；这不是诊断。", "Your score reaches the common threshold of 20. A professional assessment is suggested; this is not a diagnosis."),
        ),
        estimatedMinutes = 5,
        featured = false,
        source = "EAT-26: Garner et al. (1982)。临床与研究使用请保留来源；商业使用请先确认授权。",
        sourceEn = "EAT-26: Garner et al. (1982). Keep attribution for clinical and research use; check licensing for commercial use.",
        note = "EAT-26 不是诊断，也不能替代专业评估。",
        noteEn = "EAT-26 is not a diagnosis and does not replace professional assessment.",
    )

    private val pgsiOpt = listOf(o("从不", "Never", 0), o("偶尔", "Sometimes", 1), o("大多数时候", "Most of the time", 2), o("几乎总是", "Almost always", 3))


    val pgsi = ScaleDef(
        type = "PGSI",
        name = "问题赌博严重程度（PGSI）",
        nameEn = "Problem Gambling Severity Index (PGSI)",
        shortName = "PGSI",
        category = "行为成瘾",
        categoryEn = "Behavioural addictions",
        description = "9 项问题赌博筛查，了解过去一年赌博行为带来的风险与后果。",
        descriptionEn = "A 9-item screen of gambling risk and harms over the past year.",
        timeframe = "过去 12 个月",
        timeframeEn = "Over the past 12 months",
        instruction = "请按过去 12 个月的实际情况作答。",
        instructionEn = "Answer for the past 12 months.",
        questions = listOf(
            q("你是否赌博超过了自己能承受的金额", "Have you bet more than you could really afford to lose", pgsiOpt),
            q("你是否需要下更大的赌注才能获得同样的刺激", "Have you needed to gamble with larger amounts of money to get the same feeling of excitement", pgsiOpt),
            q("输钱后，你是否想再赌一次把钱赢回来", "When you gambled, did you go back another day to try to win back the money you lost", pgsiOpt),
            q("你是否借钱或卖东西来赌博", "Have you borrowed money or sold anything to get money to gamble", pgsiOpt),
            q("你是否觉得自己可能有赌博问题", "Have you felt that you might have a problem with gambling", pgsiOpt),
            q("赌博是否导致你的健康问题，包括压力或焦虑", "Has gambling caused you any health problems, including stress or anxiety", pgsiOpt),
            q("是否有人批评你的赌博，或说你赌博有问题", "Have people criticised your betting or told you that you had a gambling problem", pgsiOpt),
            q("赌博是否给你的财务造成问题", "Has your gambling caused any financial problems for you or your household", pgsiOpt),
            q("你是否因赌博而感到内疚", "Have you felt guilty about the way you gamble or what happens when you gamble", pgsiOpt),
        ),
        bands = listOf(
            b(0, 0, "non_problem", "无问题赌博信号", "No problem gambling", "目前没有明显的问题赌博信号。", "No signs of problem gambling at present."),
            b(1, 4, "low", "低风险", "Low risk", "存在低风险赌博行为。留意花费和时间。", "Some low-risk gambling behaviour. Keep an eye on money and time spent."),
            b(5, 7, "moderate", "中度风险", "Moderate risk", "存在中度风险，可能已经出现负面后果。建议减少赌博并寻求支持。", "Moderate risk with possible harms. Consider cutting down and seeking support."),
            b(8, 27, "problem", "可能有问题赌博", "Possible problem gambling", "筛查提示可能存在赌博问题。建议尽快寻求专业帮助。", "The screen suggests possible problem gambling. Seek professional help soon."),
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "PGSI: Ferris & Wynne (2001), CPGI. 可免费用于非商业筛查。",
        sourceEn = "PGSI: Ferris & Wynne (2001), CPGI. Freely available for non-commercial screening.",
        note = "PGSI 不是诊断。",
        noteEn = "PGSI is not a diagnosis.",
    )



    val crafft = ScaleDef(
        type = "CRAFFT",
        name = "青少年物质使用筛查（CRAFFT）",
        nameEn = "CRAFFT Substance Use Screen",
        shortName = "CRAFFT",
        category = "物质使用",
        categoryEn = "Substance use",
        description = "6 项青少年酒精与药物使用筛查，也适用于回顾 12 个月内的使用情况。",
        descriptionEn = "A 6-item screen for adolescent alcohol and drug use over the past 12 months.",
        timeframe = "过去 12 个月",
        timeframeEn = "Over the past 12 months",
        instruction = "请按过去 12 个月的实际情况回答是或否。",
        instructionEn = "Answer yes or no for the past 12 months.",
        questions = listOf(
            q("你是否曾在饮酒或用药后乘坐由他人驾驶的汽车", "Have you ever ridden in a car driven by someone (including yourself) who was high or had been using alcohol or drugs", yesNo),
            q("你是否曾用酒精或药物来放松、感觉更好或融入", "Do you ever use alcohol or drugs to relax, feel better about yourself, or fit in", yesNo),
            q("你是否曾独自一人时饮酒或使用药物", "Do you ever use alcohol or drugs while you are by yourself, alone", yesNo),
            q("你是否曾忘记自己做过的事", "Do you ever forget things you did while using alcohol or drugs", yesNo),
            q("家人或朋友是否曾告诉你应该减少饮酒或用药", "Do your family or friends ever tell you that you should cut down on your drinking or drug use", yesNo),
            q("你是否曾因饮酒或用药而惹上麻烦", "Have you ever gotten into trouble while you were using alcohol or drugs", yesNo),
        ),
        bands = listOf(
            b(0, 1, "negative", "阴性", "Negative", "目前筛查为阴性。继续留意风险情境。", "The screen is currently negative. Stay aware of risky situations."),
            b(2, 6, "positive", "阳性", "Positive", "筛查为阳性。建议由专业人员进一步评估，并与信任的成年人或医生讨论。", "The screen is positive. A professional assessment is suggested; talk with a trusted adult or doctor."),
        ),
        estimatedMinutes = 2,
        featured = false,
        source = "CRAFFT: Knight et al. (1999, 2002). 临床与研究使用请保留来源。",
        sourceEn = "CRAFFT: Knight et al. (1999, 2002). Keep attribution for clinical and research use.",
        note = "CRAFFT 不是诊断。",
        noteEn = "CRAFFT is not a diagnosis.",
    )

    private val des = listOf(o("0%（从未）", "0% (never)", 0), o("10%", "10%", 10), o("20%", "20%", 20), o("30%", "30%", 30), o("40%", "40%", 40), o("50%", "50%", 50), o("60%", "60%", 60), o("70%", "70%", 70), o("80%", "80%", 80), o("90%", "90%", 90), o("100%（总是）", "100% (always)", 100))


    val des2 = ScaleDef(
        type = "DES2",
        name = "解离体验量表（DES-II）",
        nameEn = "Dissociative Experiences Scale-II (DES-II)",
        shortName = "DES-II",
        category = "创伤与应激",
        categoryEn = "Trauma & stress",
        description = "28 项解离体验自评，了解日常生活中解离体验出现的比例（最终取平均分）。",
        descriptionEn = "A 28-item measure of the percentage of time dissociative experiences occur in daily life (scored as a mean).",
        timeframe = "日常生活中",
        timeframeEn = "In daily life",
        instruction = "请按每项体验在你生活中出现的百分比作答，不必纠结精确数字。",
        instructionEn = "For each experience, choose the percentage of time it happens to you. Do not worry about being exact.",
        questions = listOf(
            q("忘记自己刚做过的事", "Finding yourself in a place and not knowing how you got there", des),
            q("别人说你说过的话，但你没有印象", "Being told you have said things you do not remember saying", des),
            q("发现自己身处某地，却不知道是怎么到那里的", "Finding yourself somewhere and not knowing how you got there", des),
            q("发现自己穿着衣服，却不知道是怎么穿上的", "Finding yourself dressed and not remembering how you got dressed", des),
            q("发现身边有东西，却不知道是怎么来的", "Finding things among your belongings that you do not remember getting", des),
            q("被陌生人接近，对方说你认识他", "Being approached by a stranger who says you know them", des),
            q("有时觉得身体不属于自己", "Feeling that your body does not belong to you", des),
            q("有时觉得自己像旁观者在看自己", "Feeling as if you are looking at yourself from the outside", des),
            q("不记得重要的人生事件", "Not remembering important events in your life", des),
            q("别人说你撒谎，但你相信自己没有", "Being accused of lying when you believe you are telling the truth", des),
            q("照镜子时认不出自己", "Not recognising yourself in a mirror", des),
            q("觉得周围世界不真实", "Feeling that the world around you is not real", des),
            q("觉得自己像机器人或没有生命", "Feeling like a robot or that you have no control over your actions", des),
            q("有时回忆过去，像真的一样重新经历", "Remembering a past event so vividly that you feel you are reliving it", des),
            q("不记得自己是否做过某件事", "Being unsure whether you have done something or just thought about it", des),
            q("熟悉的地方变得陌生", "Finding a familiar place strange or unfamiliar", des),
            q("被电影或故事吸引，完全不知道周围发生什么", "Becoming so absorbed in a film or story that you are unaware of your surroundings", des),
            q("专注于某事时，疼痛感消失", "Being able to ignore pain", des),
            q("有时盯着空白发呆，什么也不想", "Staring into space and feeling as if you are not thinking", des),
            q("别人叫你时，你听不到", "Not hearing someone call your name", des),
            q("独自时大声说话", "Talking out loud to yourself when you are alone", des),
            q("在不同场合表现得像两个人", "Acting so differently in different situations that you feel like two people", des),
            q("有时觉得内心有不同声音在说话", "Feeling that there are voices inside your head that tell you what to do", des),
            q("有时觉得能完成别人做不到的事", "Feeling that you can do things others cannot", des),
            q("有时不知道自己是自己", "Feeling unsure of who you really are", des),
            q("有时看到东西变形", "Seeing things around you differently from how others see them", des),
            q("有时身体疼痛，但查不出原因", "Having physical pain that doctors cannot explain", des),
            q("有时觉得被附身或变成了别人", "Feeling that you are possessed or that you have become someone else", des),
        ),
        bands = listOf(
            b(0, 29, "low", "解离体验较少", "Low dissociation", "解离体验较少。如仍有不真实感或记忆空白，可以和专业人士讨论。", "Dissociative experiences are low. If you still have unreality or memory gaps, discuss it with a professional."),
            b(30, 49, "moderate", "中等程度解离", "Moderate dissociation", "解离体验处于中等水平。建议由熟悉创伤的专业人士进一步评估。", "Dissociative experiences are moderate. Consider an assessment by a trauma-informed professional."),
            b(50, 100, "high", "较高程度解离", "High dissociation", "解离体验较多。建议寻求专业评估；这不是诊断，也不等于严重疾病。", "Dissociative experiences are frequent. Seek professional assessment; this is not a diagnosis."),
        ),
        scoring = ScaleScoring.MEAN,
        maxScoreOverride = 100,
        estimatedMinutes = 5,
        featured = false,
        source = "DES-II: Carlson & Putnam (1993). 临床与研究使用可免费使用并需保留来源；商业分发请先取得作者许可。",
        sourceEn = "DES-II: Carlson & Putnam (1993). Free for clinical and research use with attribution; obtain permission for commercial distribution.",
        note = "中文条目为本项目自译，非官方中文版；得分是 28 项的平均值，仅用于自我观察。",
        noteEn = "The Chinese items are our own translation, not an official version; the score is the mean of 28 items and is for self-observation only.",
    )



    val asq = ScaleDef(
        type = "ASQ",
        name = "自杀风险筛查（ASQ）",
        nameEn = "Ask Suicide-Screening Questions (ASQ)",
        shortName = "ASQ",
        category = "风险评估",
        categoryEn = "Risk screening",
        description = "4 项简短自杀风险筛查。任何一项为“是”都值得立即获得支持。",
        descriptionEn = "A 4-item suicide risk screen. Any yes answer deserves immediate support.",
        timeframe = "过去几周 / 一生",
        timeframeEn = "Past few weeks / lifetime",
        instruction = "请如实作答。如果你现在处于危险中，请立即联系当地急救或危机热线。",
        instructionEn = "Answer honestly. If you are in immediate danger, contact local emergency services or a crisis line now.",
        questions = listOf(
            q("过去几周里，你是否曾希望自己已经死了，或希望自己不再醒来", "In the past few weeks, have you wished you were dead or wished you could go to sleep and not wake up", asqNoFirst),
            q("过去几周里，你是否曾觉得如果你死了，你或你的家人会过得更好", "In the past few weeks, have you felt that you or your family would be better off if you were dead", asqNoFirst),
            q("过去一周里，你是否曾有过结束自己生命的念头", "In the past week, have you been having thoughts about killing yourself", asqNoFirst),
            q("你是否曾经尝试过自杀", "Have you ever tried to kill yourself", asqNoFirst),
        ),
        bands = listOf(
            b(0, 0, "negative", "没有阳性回答", "No positive answers", "目前没有筛查阳性回答。如果情绪变差，随时可以再做一次或联系信任的人。", "No positive answers right now. If things get worse, you can take this again or reach out to someone you trust."),
            b(1, 4, "positive", "筛查阳性", "Positive screen", "有一项或以上为“是”。请尽快联系信任的人、医生或危机热线；如果现在有危险，请立即拨打当地急救电话。", "One or more yes answers. Please contact someone you trust, a doctor or a crisis line soon; if you are in danger now, call local emergency services."),
        ),
        estimatedMinutes = 1,
        featured = false,
        source = "ASQ: NIMH / Horowitz et al. (2012). 可免费用于临床与研究筛查。",
        sourceEn = "ASQ: NIMH / Horowitz et al. (2012). Freely available for clinical and research screening.",
        note = "ASQ 只用于筛查，不是诊断；阳性不等于一定会发生危险，但需要被认真对待。",
        noteEn = "ASQ is a screen, not a diagnosis; a positive result does not mean something will happen, but it must be taken seriously.",
        selfHarmQuestionIndex = 0,
    )


    private val bslOptions = listOf(
        o("一点也没有", "Not at all", 0),
        o("有点困扰", "A little", 1),
        o("相当困扰", "Rather", 2),
        o("非常困扰", "Much", 3),
        o("很严重的困扰", "Very strong", 4)
    )

    private val bslSupplementOptions = listOf(
        o("一次也没有", "Not at all", 0),
        o("有过一次", "Once", 1),
        o("出现 2–3 次", "2–3 times", 2),
        o("出现 4–6 次", "4–6 times", 3),
        o("每天都有或更常出现", "Daily or more often", 4)
    )

    val bsl23 = ScaleDef(
        type = "BSL23",
        name = "边缘型症状量表（BSL-23）",
        nameEn = "Borderline Symptom List (BSL-23)",
        shortName = "BSL-23",
        category = "人格与情绪调节",
        categoryEn = "Personality & emotion regulation",
        description = "23 项边缘型症状自评，得分是 23 题的平均分；另含 11 项不计分的行为补充题。",
        descriptionEn = "A 23-item measure of borderline symptoms scored as the mean of 23 items, plus 11 unscored behaviour items.",
        timeframe = "过去一周",
        timeframeEn = "Over the past week",
        instruction = "请评定过去一星期里，以下困难或问题给你带来的困扰程度。如果某一周内感受有高有低，请按平均情况作答。最后 11 题询问行为，不计入总分。",
        instructionEn = "Rate how much each difficulty bothered you over the past week. If things varied, give an average rating. The final 11 items ask about behaviours and are not included in the total score.",
        questions = listOf(
            q("我很难专心", "It was hard for me to concentrate", bslOptions),
            q("我觉得很无助", "I felt helpless", bslOptions),
            q("我会心不在焉，记不得我做过什么事", "I was absent-minded and unable to remember what I was actually doing", bslOptions),
            q("我觉得厌恶", "I felt disgust", bslOptions),
            q("我考虑要伤害自己", "I thought of hurting myself", bslOptions),
            q("我不信任别人", "I didn't trust other people", bslOptions),
            q("我不觉得自己有权利活下去", "I didn't believe in my right to live", bslOptions),
            q("我很寂寞", "I was lonely", bslOptions),
            q("我的精神紧绷，令我压力很大", "I experienced stressful inner tension", bslOptions),
            q("我脑海有令我非常害怕的影像", "I had images that I was very much afraid of", bslOptions),
            q("我恨我自己", "I hated myself", bslOptions),
            q("我想惩罚我自己", "I wanted to punish myself", bslOptions),
            q("我感到羞耻，因而觉得困扰", "I suffered from shame", bslOptions),
            q("我的心情反复感到焦虑、愤怒、忧郁，快速起伏变化", "My mood rapidly cycled in terms of anxiety, anger, and depression", bslOptions),
            q("我对于来自脑子里或外面的声音和噪音感到困扰", "I suffered from voices and noises from inside or outside my head", bslOptions),
            q("别人的批评会对我造成巨大的影响", "Criticism had a devastating effect on me", bslOptions),
            q("我觉得自己很脆弱", "I felt vulnerable", bslOptions),
            q("对我来说，想死的念头具有某种程度的吸引力", "The idea of death had a certain fascination for me", bslOptions),
            q("每件事情对我来说似乎都是没意义的", "Everything seemed senseless to me", bslOptions),
            q("我担心自己会失去控制", "I was afraid of losing control", bslOptions),
            q("我对自己感到厌恶", "I felt disgusted by myself", bslOptions),
            q("我觉得自己仿佛离自己很远", "I felt as if I was far away from myself", bslOptions),
            q("我觉得自己一无是处", "I felt worthless", bslOptions),
            // 以下为不计分的补充行为项目
            q("我以割伤、烧伤、勒自己或撞墙等方式伤害自己", "I hurt myself by cutting, burning, strangling, headbanging etc.", bslSupplementOptions),
            q("我告诉别人我要去自杀", "I told other people that I was going to kill myself", bslSupplementOptions),
            q("我尝试结束自己的生命", "I tried to commit suicide", bslSupplementOptions),
            q("我超量地大吃大喝", "I had episodes of binge eating", bslSupplementOptions),
            q("我催吐", "I induced vomiting", bslSupplementOptions),
            q("我做出高危险性的行为，例如故意开快车、在高楼屋顶奔跑、在窄桥上摇晃走动等", "I displayed high-risk behavior by knowingly driving too fast, running around on the roofs of high buildings, balancing on bridges, etc.", bslSupplementOptions),
            q("我喝酒喝到醉", "I got drunk", bslSupplementOptions),
            q("我使用毒品", "I took drugs", bslSupplementOptions),
            q("我未经医生处方使用镇静安眠药或止痛药，或即使有处方也服用超过剂量", "I took medication that had not been prescribed or, if prescribed, took more than the prescribed dose", bslSupplementOptions),
            q("我控制不住地发怒，或出现以肢体攻击他人", "I had outbreaks of uncontrolled anger or physically attacked others", bslSupplementOptions),
            q("我控制不住自己而与他人发生性行为，之后为此感到羞愧或生气", "I had uncontrollable sexual encounters of which I was later ashamed or which made me angry", bslSupplementOptions)
        ),
        bands = listOf(
            b(0, 2, "none_low", "症状很少或没有", "None or low", "目前边缘型症状负担很低。如果仍有困扰，可以继续记录并和信任的人讨论。", "Current borderline symptom burden is none or low. If you are still troubled, keep tracking and talk with someone you trust."),
            b(3, 10, "mild", "轻度症状负担", "Mild", "有一些边缘型相关症状。可以观察它们与压力、人际关系和睡眠的关系。", "Some borderline-related symptoms are present. Notice how they relate to stress, relationships and sleep."),
            b(11, 18, "moderate", "中度症状负担", "Moderate", "症状负担处于中等水平，可能已经影响日常功能。建议和专业人员讨论。", "The symptom burden is moderate and may affect daily functioning. Consider discussing it with a professional."),
            b(19, 26, "high", "较高症状负担", "High", "症状负担较高。建议尽快接受专业评估，并建立可用的支持与安全计划。", "The symptom burden is high. Seek professional assessment soon and build a support and safety plan."),
            b(27, 34, "very_high", "很高症状负担", "Very high", "症状负担很高，可能需要更密集的支持。请与专业人员保持联系。", "The symptom burden is very high and may need more intensive support. Stay in contact with a professional."),
            b(35, 40, "extremely_high", "极高症状负担", "Extremely high", "症状负担极高。请优先寻求专业帮助；如果出现伤害自己的念头，请立即联系信任的人或危机热线。", "The symptom burden is extremely high. Prioritise professional help; if you have thoughts of harming yourself, contact someone you trust or a crisis line now.")
        ),
        scoring = ScaleScoring.BSL23,
        maxScoreOverride = 40,
        scoreDivisor = 10,
        scoredQuestionCount = 23,
        supplementStartIndex = 23,
        estimatedMinutes = 6,
        featured = false,
        source = "BSL-23: Bohus et al. (2009)；严重程度分级: Kleindienst et al. (2020)。中文版来自 ZI Mannheim 公开下载；电子版再分发前请确认授权。",
        sourceEn = "BSL-23: Bohus et al. (2009); severity classification: Kleindienst et al. (2020). Chinese version from the ZI Mannheim public download; check permission before redistributing an electronic version.",
        note = "得分是 23 个计分题的平均分；最后 11 题为不计分的行为补充项目。分级反映症状负担，不是边缘型人格障碍的诊断。",
        noteEn = "The score is the mean of 23 scored items; the final 11 items are unscored behaviour supplements. The grades reflect symptom burden, not a diagnosis of borderline personality disorder.",
        selfHarmQuestionIndex = 4,
        riskQuestionIndices = listOf(6, 11, 17, 23, 24, 25)
    )

    private val iesrOptions = listOf(
        o("完全没有", "Not at all", 0),
        o("有一点", "A little bit", 1),
        o("中等程度", "Moderately", 2),
        o("相当多", "Quite a bit", 3),
        o("极度", "Extremely", 4)
    )

    val iesr = ScaleDef(
        type = "IESR",
        name = "事件影响量表（IES-R）",
        nameEn = "Impact of Event Scale-Revised (IES-R)",
        shortName = "IES-R",
        category = "创伤与应激",
        categoryEn = "Trauma & stress",
        description = "22 项自评，了解过去一周某件压力事件带来的侵入、回避与警觉反应。",
        descriptionEn = "A 22-item measure of intrusion, avoidance and hyperarousal during the past week after a stressful event.",
        timeframe = "过去 7 天",
        timeframeEn = "Past 7 days",
        instruction = "请回想过去一周里让你感到压力或痛苦的事件；如果有多个，请以最困扰你的一个为准。然后评定这些困难对你的困扰程度。",
        instructionEn = "Think of a stressful or distressing event from the past week; if there were several, use the one that bothers you most. Rate how much each difficulty distressed you.",
        questions = listOf(
            q("任何提醒都会让我回想起与它有关的感受", "Any reminder brought back feelings about it", iesrOptions),
            q("我难以保持睡眠", "I had trouble staying asleep", iesrOptions),
            q("其他事情总让我想起它", "Other things kept making me think about it", iesrOptions),
            q("我感到易怒和愤怒", "I felt irritable and angry", iesrOptions),
            q("当我想到它或被提醒时，我会避免让自己难过", "I avoided letting myself get upset when I thought about it or was reminded of it", iesrOptions),
            q("我会不由自主地想到它", "I thought about it when I didn't mean to", iesrOptions),
            q("我觉得它仿佛没有发生过，或不真实", "I felt as if it hadn't happened or it wasn't real", iesrOptions),
            q("我避开与它有关的提醒", "I stayed away from reminders of it", iesrOptions),
            q("与它有关的画面会突然出现在我脑海中", "Pictures about it popped into my mind", iesrOptions),
            q("我容易受惊", "I was jumpy and easily startled", iesrOptions),
            q("我努力不去想它", "I tried not to think about it", iesrOptions),
            q("我知道自己对它还有很多感受，但没有去处理", "I was aware that I still had a lot of feelings about it, but I didn't deal with them", iesrOptions),
            q("我对它的感受有些麻木", "My feelings about it were kind of numb", iesrOptions),
            q("我发现自己的行为或感受像回到了那个时候", "I found myself acting or feeling like I was back at that time", iesrOptions),
            q("我难以入睡", "I had trouble falling asleep", iesrOptions),
            q("我对它有一阵阵强烈的情绪", "I had waves of strong feelings about it", iesrOptions),
            q("我试图把它从记忆中抹去", "I tried to remove it from my memory", iesrOptions),
            q("我难以集中注意力", "I had trouble concentrating", iesrOptions),
            q("与它有关的提醒会引起身体反应，比如出汗、呼吸困难、恶心或心跳加速", "Reminders of it caused me to have physical reactions, such as sweating, trouble breathing, nausea, or a pounding heart", iesrOptions),
            q("我梦到过它", "I had dreams about it", iesrOptions),
            q("我感到警觉、戒备", "I felt watchful and on-guard", iesrOptions),
            q("我努力不去谈论它", "I tried not to talk about it", iesrOptions)
        ),
        bands = listOf(
            b(0, 23, "low", "应激反应较低", "Low distress", "目前与事件相关的应激症状较轻。如果症状延迟出现或加重，可以再做一次。", "Event-related stress symptoms are currently low. If they emerge later or worsen, take it again."),
            b(24, 32, "concern", "值得关注", "Worth attention", "达到 24 分及以上，创伤相关症状值得关注。建议和信任的人或专业人员谈谈。", "A score of 24 or more suggests trauma-related symptoms worth attention. Consider talking with someone you trust or a professional."),
            b(33, 36, "probable", "可能达到 PTSD 筛查界值", "Possible PTSD threshold", "33 分及以上常被用作 PTSD 的可能界值。请由专业人员进一步评估。", "A score of 33 or more is often used as a probable PTSD threshold. Seek further assessment by a professional."),
            b(37, 88, "severe", "应激反应较重", "High distress", "症状负担较重，建议尽快寻求专业评估与支持。", "The symptom burden is high. Seek professional assessment and support soon.")
        ),
        maxScoreOverride = 88,
        estimatedMinutes = 5,
        featured = false,
        source = "IES-R: Weiss & Marmar (1997). 量表受版权保护；电子版再分发前需取得作者书面许可。",
        sourceEn = "IES-R: Weiss & Marmar (1997). The scale is copyrighted; obtain written permission from the authors before redistributing an electronic version.",
        note = "本应用不记录具体事件；请按最困扰你的事件作答。得分反映近一周的应激反应，不是 PTSD 诊断。",
        noteEn = "The app does not record the specific event; answer about the event that troubles you most. The score reflects the past week and is not a PTSD diagnosis."
    )

    private val spinOptions = listOf(
        o("完全不符合", "Not at all", 0),
        o("有一点符合", "A little bit", 1),
        o("有些符合", "Somewhat", 2),
        o("非常符合", "Very much", 3),
        o("极度符合", "Extremely", 4)
    )

    val spin = ScaleDef(
        type = "SPIN",
        name = "社交恐惧量表（SPIN）",
        nameEn = "Social Phobia Inventory (SPIN)",
        shortName = "SPIN",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "17 项自评，了解过去一周社交焦虑相关的恐惧、回避与身体反应。",
        descriptionEn = "A 17-item measure of fear, avoidance and physiological arousal related to social anxiety over the past week.",
        timeframe = "过去一周",
        timeframeEn = "Past week",
        instruction = "请评定过去一周里，以下问题对你的困扰程度。",
        instructionEn = "Rate how much each problem has bothered you during the past week.",
        questions = listOf(
            q("我害怕权威人士", "I am afraid of people in authority", spinOptions),
            q("在别人面前脸红让我困扰", "I am bothered by blushing in front of people", spinOptions),
            q("聚会和社交活动让我害怕", "Parties and social events scare me", spinOptions),
            q("我避免和不认识的人说话", "I avoid talking to people I don't know", spinOptions),
            q("被批评让我非常害怕", "Being criticized scares me a lot", spinOptions),
            q("害怕尴尬使我避免做某些事或与人说话", "Fear of embarrassment causes me to avoid doing things or speaking to people", spinOptions),
            q("在别人面前出汗让我痛苦", "Sweating in front of people causes me distress", spinOptions),
            q("我避免参加聚会", "I avoid going to parties", spinOptions),
            q("我避免参加成为众人注意焦点的活动", "I avoid activities in which I am the center of attention", spinOptions),
            q("和陌生人说话让我害怕", "Talking to strangers scares me", spinOptions),
            q("我避免必须发表演讲的场合", "I avoid having to give speeches", spinOptions),
            q("为了避免被批评，我愿意做任何事", "I would do anything to avoid being criticized", spinOptions),
            q("在人群中时，心悸让我困扰", "Heart palpitations bother me when I am around people", spinOptions),
            q("当别人可能看着我时，我害怕做事", "I am afraid of doing things when people might be watching", spinOptions),
            q("尴尬或显得愚蠢是我最害怕的事情之一", "Being embarrassed or looking stupid is among my worst fears", spinOptions),
            q("我避免和任何权威人士说话", "I avoid speaking to anyone in authority", spinOptions),
            q("在别人面前颤抖或发抖让我痛苦", "Trembling or shaking in front of others is distressing to me", spinOptions)
        ),
        bands = listOf(
            b(0, 20, "none", "无明显社交焦虑", "No clear social anxiety", "得分低于常见筛查界值。如果仍然困扰你，可以和专业人员讨论。", "The score is below common screening thresholds. If it still troubles you, discuss it with a professional."),
            b(21, 30, "mild", "轻度社交焦虑", "Mild", "有一些社交焦虑症状。可以记录触发情境，并练习逐步面对。", "Some social anxiety symptoms are present. Track triggers and consider gradual exposure practice."),
            b(31, 40, "moderate", "中度社交焦虑", "Moderate", "社交焦虑可能已经影响生活。建议寻求专业评估。", "Social anxiety may be affecting your life. Consider professional assessment."),
            b(41, 50, "severe", "重度社交焦虑", "Severe", "症状较重，建议尽快与专业人员讨论治疗方案。", "Symptoms are severe. Consider discussing treatment with a professional soon."),
            b(51, 68, "very_severe", "极重度社交焦虑", "Very severe", "症状负担很重。请优先寻求专业帮助。", "The symptom burden is very severe. Prioritise professional help.")
        ),
        maxScoreOverride = 68,
        estimatedMinutes = 3,
        featured = false,
        source = "SPIN: Connor et al. (2000). 量表受版权保护；再分发需取得版权方书面许可。",
        sourceEn = "SPIN: Connor et al. (2000). The scale is copyrighted; written permission is required before redistribution.",
        note = "19 分左右常被用作筛查参考；分数不是社交焦虑障碍的诊断。",
        noteEn = "A score around 19 is often used as a screening reference; this is not a diagnosis of social anxiety disorder."
    )

    private val cssrsOptions = listOf(o("否", "No", 0), o("是", "Yes", 1))

    val cssrs = ScaleDef(
        type = "CSSRS",
        name = "哥伦比亚自杀严重程度筛查（C-SSRS）",
        nameEn = "Columbia-Suicide Severity Rating Scale (C-SSRS, screener)",
        shortName = "C-SSRS",
        category = "风险评估",
        categoryEn = "Risk screening",
        description = "6 个筛查问题，了解过去一个月的自杀意念与近三个月的自杀行为，并给出风险分层。",
        descriptionEn = "Six screener questions about suicidal ideation in the past month and behaviour in the past three months, with risk triage.",
        timeframe = "过去一个月（行为：过去 3 个月）",
        timeframeEn = "Past month (behaviour: past 3 months)",
        instruction = "请按最近的情况如实作答。任何一项为「是」都值得被认真对待，建议尽快告诉信任的人或专业人员。",
        instructionEn = "Answer honestly about recent experiences. Any yes answer deserves attention; consider telling someone you trust or a professional soon.",
        questions = listOf(
            q("你是否希望自己已经死了，或希望自己睡着后不再醒来？", "Have you wished you were dead or wished you could go to sleep and not wake up?", cssrsOptions),
            q("你是否真的有过自杀的念头？", "Have you actually had any thoughts of killing yourself?", cssrsOptions),
            q("你是否想过可能怎样结束自己的生命？", "Have you thought about how you might do this?", cssrsOptions),
            q("你是否对这些念头有过行动的意图（而不只是想想、确定自己不会去做）？", "Have you had any intention of acting on these thoughts of killing yourself, as opposed to you have the thoughts but you definitely would not act on them?", cssrsOptions),
            q("你是否已经开始计划或想清楚结束生命的具体细节，并且打算实施？", "Have you started to work out, or actually worked out, the specific details of how to kill yourself and did you actually intend to carry out the details of your plan?", cssrsOptions),
            q("你是否曾经做过任何事、开始做任何事或准备做任何事来结束自己的生命？", "Have you ever done anything, started to do anything, or prepared to do anything to end your life?", cssrsOptions),
            q("如果上一题选「是」，这件事是否发生在过去 3 个月内？", "If you answered yes to the previous question, was this within the past three months?", listOf(o("不适用（上一题选「否」）", "Not applicable (previous answer was no)", 0), o("否", "No", 0), o("是", "Yes", 1)))
        ),
        bands = listOf(
            b(0, 0, "negative", "没有阳性回答", "No positive answers", "目前没有筛查阳性回答。如果情绪变差，随时可以再做一次或联系信任的人。", "No positive answers right now. If things get worse, you can take this again or reach out to someone you trust."),
            b(1, 7, "moderate", "需要进一步评估", "Further assessment needed", "有阳性回答，但未达到高风险条目。请尽快联系专业人员做更完整的风险评估。", "There are positive answers but no high-risk item. Contact a professional soon for a fuller risk assessment."),
            b(1, 7, "high", "高风险，请立即寻求帮助", "High risk — seek help now", "第 4、5 题或近三个月的自杀行为为「是」，属于高风险。请不要独处，立即联系信任的人、专业人员或急救服务。", "A yes on item 4, 5 or recent suicidal behaviour indicates high risk. Do not stay alone; contact someone you trust, a professional, or emergency services now.")
        ),
        scoring = ScaleScoring.CSSRS,
        maxScoreOverride = 6,
        estimatedMinutes = 2,
        featured = false,
        source = "C-SSRS: Posner et al. (2011) / Columbia Lighthouse Project。社区与医疗场景可免费使用，但电子版实现可能需经 Columbia/RFMH 与 Clario 授权；发布前请确认。",
        sourceEn = "C-SSRS: Posner et al. (2011) / Columbia Lighthouse Project. Free for community and healthcare use, but electronic implementations may require a license from Columbia/RFMH and Clario; confirm before release.",
        note = "这是筛查，不是完整风险评估。任何阳性回答都应尽快由专业人员进一步评估。",
        noteEn = "This is a screener, not a full risk assessment. Any positive answer should be followed up by a professional soon.",
        selfHarmQuestionIndex = 1,
        riskQuestionIndices = listOf(0, 2, 3, 4, 5, 6)
    )

    private val pqOptions = listOf(o("否", "False", 0), o("是", "True", 1))

    val pq16 = ScaleDef(
        type = "PQ16",
        name = "精神病性体验筛查（PQ-16）",
        nameEn = "Prodromal Questionnaire – 16 items (PQ-16)",
        shortName = "PQ-16",
        category = "精神病性体验",
        categoryEn = "Psychotic experiences",
        description = "16 项是/否筛查，了解近期可能的精神病性体验；阳性条目数达到 6 项时建议进一步评估。",
        descriptionEn = "A 16-item yes/no screen for recent psychotic-like experiences; 6 or more positive items suggest further assessment.",
        timeframe = "过去一个月",
        timeframeEn = "Past month",
        instruction = "请选择过去一个月里是否出现过以下体验。这里只统计「是」的条目数，不评价严重程度。",
        instructionEn = "Choose whether each experience occurred in the past month. Only the number of yes answers is counted.",
        questions = listOf(
            q("我对过去喜欢的事情提不起兴趣", "I feel uninterested in the things I used to enjoy.", pqOptions),
            q("我常常觉得经历的事情仿佛以前发生过（似曾相识）", "I often seem to live through events exactly as they happened before (déjà vu).", pqOptions),
            q("我有时会闻到或尝到别人闻不到或尝不到的东西", "I sometimes smell or taste things that other people can't smell or taste.", pqOptions),
            q("我经常听到不寻常的声音，比如敲击声、咔嗒声、嘶嘶声、拍手声或耳鸣", "I often hear unusual sounds like banging, clicking, hissing, clapping or ringing in my ears.", pqOptions),
            q("我有时分不清自己的体验是真实的还是想象的", "I have been confused at times whether something I experienced was real or imaginary.", pqOptions),
            q("当我看着某人或镜子里的自己时，我看到面孔在眼前发生变化", "When I look at a person, or look at myself in a mirror, I have seen the face change right before my eyes.", pqOptions),
            q("初次见人时，我会极度焦虑", "I get extremely anxious when meeting people for the first time.", pqOptions),
            q("我看到过别人显然看不到的东西", "I have seen things that other people apparently can't see.", pqOptions),
            q("我的想法有时强烈到几乎能听见", "My thoughts are sometimes so strong that I can almost hear them.", pqOptions),
            q("我有时会在广告、橱窗或周围物品的摆放中看出特殊含义", "I sometimes see special meanings in advertisements, shop windows, or in the way things are arranged around me.", pqOptions),
            q("有时我觉得自己无法控制自己的想法或念头", "Sometimes I have felt that I'm not in control of my own ideas or thoughts.", pqOptions),
            q("我有时会突然被远处的声音分散注意力，而平时不会注意到这些声音", "Sometimes I feel suddenly distracted by distant sounds that I am not normally aware of.", pqOptions),
            q("我听到过别人听不到的声音，比如有人低语或说话", "I have heard things other people can't hear like voices of people whispering or talking.", pqOptions),
            q("我常常觉得别人在针对我", "I often feel that others have it in for me.", pqOptions),
            q("我有过某种人或力量在附近的感觉，即使我看不到任何人", "I have had the sense that some person or force is around me, even though I could not see anyone.", pqOptions),
            q("我觉得身体的某些部位发生了变化，或运作方式与以前不同", "I feel that parts of my body have changed in some way, or that parts of my body are working differently than before.", pqOptions)
        ),
        bands = listOf(
            b(0, 5, "below", "未达到常见筛查界值", "Below common screening threshold", "阳性条目少于 6 项。如果这些体验仍然困扰你，可以和专业人员讨论。", "Fewer than 6 positive items. If these experiences still trouble you, discuss them with a professional."),
            b(6, 16, "above", "建议进一步评估", "Further assessment suggested", "阳性条目达到 6 项及以上，建议由熟悉精神病性体验的专业人员进一步评估。这并不是诊断。", "Six or more positive items suggest further assessment by a professional familiar with psychotic experiences. This is not a diagnosis.")
        ),
        maxScoreOverride = 16,
        estimatedMinutes = 3,
        featured = false,
        source = "PQ-16: Ising et al. (2012) / Loewy et al. (2005). 量表公开发表；电子或商业再分发前建议联系开发者确认许可。",
        sourceEn = "PQ-16: Ising et al. (2012) / Loewy et al. (2005). Published openly; confirm permission with the developers before electronic or commercial redistribution.",
        note = "结果只统计「是」的条目数，不代表诊断。睡眠不足、压力或物质使用也可能造成类似体验。",
        noteEn = "The result counts yes answers and is not a diagnosis. Sleep loss, stress or substance use can also cause similar experiences."
    )

    private val essOptions = listOf(
        o("从不打瞌睡", "Would never doze", 0),
        o("轻微可能", "Slight chance of dozing", 1),
        o("中等可能", "Moderate chance of dozing", 2),
        o("很大可能", "High chance of dozing", 3)
    )

    val ess = ScaleDef(
        type = "ESS",
        name = "日间嗜睡量表（ESS）",
        nameEn = "Epworth Sleepiness Scale (ESS)",
        shortName = "ESS",
        category = "睡眠",
        categoryEn = "Sleep",
        description = "8 项自评，了解日常生活中打瞌睡的可能性。",
        descriptionEn = "An 8-item measure of the likelihood of dozing in everyday situations.",
        timeframe = "近期通常情况",
        timeframeEn = "Usual way of life recently",
        instruction = "请判断在以下情境中打瞌睡或睡着的可能性（不是仅仅感到疲倦）。即使最近没有做过这些事，也请估计当时会怎样。",
        instructionEn = "Rate the likelihood of dozing or falling asleep (not just feeling tired) in each situation. Even if you have not done some recently, estimate how they would have affected you.",
        questions = listOf(
            q("坐着阅读时", "Sitting and reading", essOptions),
            q("看电视时", "Watching TV", essOptions),
            q("在公共场所坐着不动时（如剧院或会议）", "Sitting, inactive in a public place (e.g. a theater or a meeting)", essOptions),
            q("乘车一小时以上不休息时", "As a passenger in a car for an hour without a break", essOptions),
            q("下午躺下休息时（条件允许）", "Lying down to rest in the afternoon when circumstances permit", essOptions),
            q("坐着与人交谈时", "Sitting and talking to someone", essOptions),
            q("午饭后安静坐着（未饮酒）时", "Sitting quietly after a lunch without alcohol", essOptions),
            q("开车时遇到堵车停了几分钟时", "In a car, while stopped for a few minutes in the traffic", essOptions)
        ),
        bands = listOf(
            b(0, 5, "lower_normal", "日间嗜睡较低", "Lower normal daytime sleepiness", "日间嗜睡程度较低。", "Daytime sleepiness is in the lower normal range."),
            b(6, 10, "higher_normal", "日间嗜睡偏高但仍在常见范围", "Higher normal daytime sleepiness", "日间嗜睡偏高。可以留意睡眠时长、作息规律和药物影响。", "Daytime sleepiness is in the higher normal range. Notice sleep duration, schedule regularity and medication effects."),
            b(11, 12, "mild", "轻度日间嗜睡", "Mild excessive daytime sleepiness", "达到 11 分及以上，属于日间嗜睡偏高。建议与医生讨论睡眠和药物因素。", "A score of 11 or more suggests excessive daytime sleepiness. Discuss sleep and medication factors with a doctor."),
            b(13, 15, "moderate", "中度日间嗜睡", "Moderate excessive daytime sleepiness", "中度日间嗜睡，建议进一步评估睡眠障碍或药物影响。", "Moderate excessive daytime sleepiness. Consider assessment for sleep disorders or medication effects."),
            b(16, 24, "severe", "重度日间嗜睡", "Severe excessive daytime sleepiness", "重度日间嗜睡，建议尽快寻求专业评估；开车或操作机械时请格外小心。", "Severe excessive daytime sleepiness. Seek professional assessment soon and take extra care when driving or operating machinery.")
        ),
        maxScoreOverride = 24,
        estimatedMinutes = 2,
        featured = false,
        source = "ESS © M.W. Johns 1990–1997。任何使用均需通过 Mapi Research Trust 取得许可；发布前请确认。",
        sourceEn = "ESS © M.W. Johns 1990–1997. Any use requires a license through Mapi Research Trust; confirm before release.",
        note = "本结果只反映打瞌睡的可能性，不是嗜睡症的诊断。",
        noteEn = "This reflects the likelihood of dozing, not a diagnosis of a sleep disorder."
    )

    private val mspssOptions = listOf(
        o("非常不同意", "Very strongly disagree", 1),
        o("不同意", "Strongly disagree", 2),
        o("有点不同意", "Mildly disagree", 3),
        o("中立", "Neutral", 4),
        o("有点同意", "Mildly agree", 5),
        o("同意", "Strongly agree", 6),
        o("非常同意", "Very strongly agree", 7)
    )

    val mspss = ScaleDef(
        type = "MSPSS",
        name = "领悟社会支持量表（MSPSS）",
        nameEn = "Multidimensional Scale of Perceived Social Support (MSPSS)",
        shortName = "MSPSS",
        category = "社会与功能",
        categoryEn = "Social & functioning",
        description = "12 项自评，了解来自特别的人、家人和朋友的社会支持。",
        descriptionEn = "A 12-item measure of perceived support from a significant other, family and friends.",
        timeframe = "目前",
        timeframeEn = "Right now",
        instruction = "请阅读每句话，选择最符合你当前感受的选项。",
        instructionEn = "Read each statement and choose how you feel about it right now.",
        questions = listOf(
            q("在我需要时，有一个特别的人会在我身边", "There is a special person who is around when I am in need.", mspssOptions),
            q("有一个特别的人，我可以和他/她分享快乐与悲伤", "There is a special person with whom I can share joys and sorrows.", mspssOptions),
            q("我的家人真的尽力帮助我", "My family really tries to help me.", mspssOptions),
            q("我能从家人那里得到我需要的情感帮助与支持", "I get the emotional help and support I need from my family.", mspssOptions),
            q("有一个特别的人是我真正的安慰来源", "I have a special person who is a real source of comfort to me.", mspssOptions),
            q("我的朋友真的尽力帮助我", "My friends really try to help me.", mspssOptions),
            q("事情不顺利时，我可以依靠我的朋友", "I can count on my friends when things go wrong.", mspssOptions),
            q("我可以和家人谈论我的问题", "I can talk about my problems with my family.", mspssOptions),
            q("我有可以分享快乐与悲伤的朋友", "I have friends with whom I can share my joys and sorrows.", mspssOptions),
            q("我的生活中有这样一个特别的人，在乎我的感受", "There is a special person in my life who cares about my feelings.", mspssOptions),
            q("我的家人愿意帮助我做决定", "My family is willing to help me make decisions.", mspssOptions),
            q("我可以和朋友谈论我的问题", "I can talk about my problems with my friends.", mspssOptions)
        ),
        bands = listOf(
            b(12, 35, "low", "社会支持偏低", "Low support", "你感受到的社会支持偏低。可以想想身边可联系的人，或从小范围的联络开始。", "Perceived social support is low. Think about people you could reach out to, even in a small way."),
            b(36, 60, "moderate", "社会支持中等", "Moderate support", "你感受到中等程度的社会支持。可以继续维护重要的人际联系。", "Perceived social support is moderate. Keep nurturing the relationships that matter to you."),
            b(61, 84, "high", "社会支持较高", "High support", "你感受到较高的社会支持。遇到困难时，记得主动使用这些支持。", "Perceived social support is high. Remember to use these supports when things are hard.")
        ),
        maxScoreOverride = 84,
        estimatedMinutes = 3,
        featured = false,
        source = "MSPSS: Zimet et al. (1988). 可免费使用，请保留引用。",
        sourceEn = "MSPSS: Zimet et al. (1988). Free to use with attribution.",
        note = "总分只反映你主观感受到的支持，不代表实际有多少人可依靠。",
        noteEn = "The total reflects perceived support, not the actual number of people available to you."
    )

    private val ffmqOptions = listOf(
        o("从不或极少符合", "Never or very rarely true", 1),
        o("很少符合", "Rarely true", 2),
        o("有时符合", "Sometimes true", 3),
        o("经常符合", "Often true", 4),
        o("非常经常或总是符合", "Very often or always true", 5)
    )

    private val ffmqReverseOptions = listOf(
        o("从不或极少符合", "Never or very rarely true", 5),
        o("很少符合", "Rarely true", 4),
        o("有时符合", "Sometimes true", 3),
        o("经常符合", "Often true", 2),
        o("非常经常或总是符合", "Very often or always true", 1)
    )

    val ffmq39 = ScaleDef(
        type = "FFMQ39",
        name = "五因素正念量表（FFMQ-39）",
        nameEn = "Five Facet Mindfulness Questionnaire (FFMQ-39)",
        shortName = "FFMQ-39",
        category = "压力与适应",
        categoryEn = "Stress & adjustment",
        description = "39 项自评，了解观察、描述、觉知行动、不评判和不反应五个正念维度。",
        descriptionEn = "A 39-item measure of observing, describing, acting with awareness, nonjudging and nonreactivity.",
        timeframe = "通常情况",
        timeframeEn = "In general",
        instruction = "请根据以下描述通常与你相符的程度作答。答案没有对错，请按真实体验选择。",
        instructionEn = "Rate how true each statement is for you in general. There are no right or wrong answers.",
        questions = listOf(
            q("走路时，我会刻意留意身体移动的感觉", "When I'm walking, I deliberately notice the sensations of my body moving.", ffmqOptions),
            q("我善于用词语描述自己的感受", "I'm good at finding words to describe my feelings.", ffmqOptions),
            q("我会因为自己有不合理或不恰当的情绪而批评自己", "I criticize myself for having irrational or inappropriate emotions.", ffmqReverseOptions),
            q("我能察觉自己的感受和情绪，而不必对它们作出反应", "I perceive my feelings and emotions without having to react to them.", ffmqOptions),
            q("我做事情时，思绪会飘走，容易分心", "When I do things, my mind wanders off and I'm easily distracted.", ffmqReverseOptions),
            q("洗澡或泡澡时，我会留意水接触身体的感觉", "When I take a shower or bath, I stay alert to the sensations of water on my body.", ffmqOptions),
            q("我能轻松地用语言表达自己的信念、观点和期望", "I can easily put my beliefs, opinions, and expectations into words.", ffmqOptions),
            q("我因为做白日梦、担心或其他分心而没有注意自己在做什么", "I don't pay attention to what I'm doing because I'm daydreaming, worrying, or otherwise distracted.", ffmqReverseOptions),
            q("我观察自己的感受，而不会陷进去", "I watch my feelings without getting lost in them.", ffmqOptions),
            q("我会告诉自己不该有现在的感受", "I tell myself I shouldn't be feeling the way I'm feeling.", ffmqReverseOptions),
            q("我会留意食物和饮料如何影响我的想法、身体感觉和情绪", "I notice how foods and drinks affect my thoughts, bodily sensations, and emotions.", ffmqOptions),
            q("我很难找到词语来描述自己正在想什么", "It's hard for me to find the words to describe what I'm thinking.", ffmqReverseOptions),
            q("我很容易分心", "I am easily distracted.", ffmqReverseOptions),
            q("我相信自己有些想法是不正常或不好的，不该那样想", "I believe some of my thoughts are abnormal or bad and I shouldn't think that way.", ffmqReverseOptions),
            q("我会留意风拂过头发或阳光照在脸上的感觉", "I pay attention to sensations, such as the wind in my hair or sun on my face.", ffmqOptions),
            q("我很难想到合适的词语来表达自己对事物的感受", "I have trouble thinking of the right words to express how I feel about things.", ffmqReverseOptions),
            q("我会判断自己的想法是好是坏", "I make judgments about whether my thoughts are good or bad.", ffmqReverseOptions),
            q("我很难专注于当下正在发生的事情", "I find it difficult to stay focused on what's happening in the present.", ffmqReverseOptions),
            q("当出现令人痛苦的想法或画面时，我会「退后一步」，觉察它而不被它吞没", "When I have distressing thoughts or images, I “step back” and am aware of the thought or image without getting taken over by it.", ffmqOptions),
            q("我会留意声音，比如钟表滴答、鸟鸣或车辆经过", "I pay attention to sounds, such as clocks ticking, birds chirping, or cars passing.", ffmqOptions),
            q("在困难情境中，我能暂停一下，而不立即作出反应", "In difficult situations, I can pause without immediately reacting.", ffmqOptions),
            q("当身体出现某种感觉时，我很难描述它，因为找不到合适的词", "When I have a sensation in my body, it's difficult for me to describe it because I can't find the right words.", ffmqReverseOptions),
            q("我似乎处于「自动运行」状态，对自己的所作所为不太有觉察", "It seems I am “running on automatic” without much awareness of what I'm doing.", ffmqReverseOptions),
            q("当出现令人痛苦的想法或画面时，我很快会平静下来", "When I have distressing thoughts or images, I feel calm soon after.", ffmqOptions),
            q("我会告诉自己不该那样想", "I tell myself that I shouldn't be thinking the way I'm thinking.", ffmqReverseOptions),
            q("我会留意事物的气味和香气", "I notice the smells and aromas of things.", ffmqOptions),
            q("即使我非常难过，我也能找到方法把它说出来", "Even when I'm feeling terribly upset, I can find a way to put it into words.", ffmqOptions),
            q("我会匆忙完成任务，而没有真正留意它们", "I rush through activities without being really attentive to them.", ffmqReverseOptions),
            q("当出现令人痛苦的想法或画面时，我能只是觉察它们而不作出反应", "When I have distressing thoughts or images I am able just to notice them without reacting.", ffmqOptions),
            q("我认为自己有些情绪是不好或不恰当的，不该有这些感受", "I think some of my emotions are bad or inappropriate and I shouldn't feel them.", ffmqReverseOptions),
            q("我会留意艺术或自然中的视觉元素，比如颜色、形状、纹理或光影图案", "I notice visual elements in art or nature, such as colors, shapes, textures, or patterns of light and shadow.", ffmqOptions),
            q("我的自然倾向是用语言描述自己的经历", "My natural tendency is to put my experiences into words.", ffmqOptions),
            q("当出现令人痛苦的想法或画面时，我只是觉察它们，然后让它们离开", "When I have distressing thoughts or images, I just notice them and let them go.", ffmqOptions),
            q("我做工作或任务时会自动完成，而不太清楚自己在做什么", "I do jobs or tasks automatically without being aware of what I'm doing.", ffmqReverseOptions),
            q("当出现令人痛苦的想法或画面时，我会根据内容判断自己好或不好", "When I have distressing thoughts or images, I judge myself as good or bad, depending what the thought/image is about.", ffmqReverseOptions),
            q("我会留意自己的情绪如何影响想法和行为", "I pay attention to how my emotions affect my thoughts and behavior.", ffmqOptions),
            q("我通常能相当详细地描述自己此刻的感受", "I can usually describe how I feel at the moment in considerable detail.", ffmqOptions),
            q("我发现自己做事时没有留心", "I find myself doing things without paying attention.", ffmqReverseOptions),
            q("当我有不合理的想法时，我会否定自己", "I disapprove of myself when I have irrational ideas.", ffmqReverseOptions)
        ),
        bands = listOf(
            b(39, 91, "lower", "正念水平较低", "Lower mindfulness", "总体来说，正念特质得分较低。可以尝试短时的呼吸觉察或身体扫描练习。", "Overall mindfulness scores are lower. Try brief breath-awareness or body-scan practices."),
            b(92, 143, "moderate", "正念水平中等", "Moderate mindfulness", "正念特质得分处于中等范围。不同维度的差异可能比总分更有意义。", "Mindfulness scores are moderate. Facet differences may be more meaningful than the total."),
            b(144, 195, "higher", "正念水平较高", "Higher mindfulness", "正念特质得分较高。继续留意各维度是否均衡发展。", "Mindfulness scores are higher. Keep noticing whether the facets are developing evenly.")
        ),
        maxScoreOverride = 195,
        estimatedMinutes = 8,
        featured = false,
        source = "FFMQ: Baer et al. (2006)。作者公开提供量表用于研究；电子或商业再分发前请联系作者确认。",
        sourceEn = "FFMQ: Baer et al. (2006). The authors provide the scale openly for research; confirm permission before electronic or commercial redistribution.",
        note = "FFMQ 没有公认的诊断界值，总分只作自我观察；五个维度建议分别理解。",
        noteEn = "The FFMQ has no validated diagnostic cutoffs; the total is for self-observation and the five facets should be interpreted separately."
    )

    private val dersOptions = listOf(
        o("几乎从不（0–10%）", "Almost never (0–10%)", 1),
        o("有时（11–35%）", "Sometimes (11–35%)", 2),
        o("大约一半时间（36–65%）", "About half the time (36–65%)", 3),
        o("大部分时间（66–90%）", "Most of the time (66–90%)", 4),
        o("几乎总是（91–100%）", "Almost always (91–100%)", 5)
    )

    private val dersReverseOptions = listOf(
        o("几乎从不（0–10%）", "Almost never (0–10%)", 5),
        o("有时（11–35%）", "Sometimes (11–35%)", 4),
        o("大约一半时间（36–65%）", "About half the time (36–65%)", 3),
        o("大部分时间（66–90%）", "Most of the time (66–90%)", 2),
        o("几乎总是（91–100%）", "Almost always (91–100%)", 1)
    )

    val ders36 = ScaleDef(
        type = "DERS36",
        name = "情绪调节困难量表（DERS-36）",
        nameEn = "Difficulties in Emotion Regulation Scale (DERS-36)",
        shortName = "DERS-36",
        category = "人格与情绪调节",
        categoryEn = "Personality & emotion regulation",
        description = "36 项自评，了解情绪的不接受、目标受阻、冲动控制、觉察、调节策略与清晰度六个维度。",
        descriptionEn = "A 36-item measure of nonacceptance, goal difficulties, impulse control, awareness, strategies and clarity in emotion regulation.",
        timeframe = "通常情况",
        timeframeEn = "In general",
        instruction = "请按以下情况在你身上出现的频率作答。答案没有对错，请按真实体验选择。",
        instructionEn = "Rate how often each statement applies to you. There are no right or wrong answers.",
        questions = listOf(
            q("我很清楚自己的感受", "I am clear about my feelings.", dersReverseOptions),
            q("我会留意自己的感受", "I pay attention to how I feel.", dersReverseOptions),
            q("我体验到的情绪让我不堪重负、失去控制", "I experience my emotions as overwhelming and out of control.", dersOptions),
            q("我不知道自己有什么感受", "I have no idea how I am feeling.", dersOptions),
            q("我很难理解自己的感受", "I have difficulty making sense out of my feelings.", dersOptions),
            q("我关注自己的感受", "I am attentive to my feelings.", dersReverseOptions),
            q("我确切知道自己有什么感受", "I know exactly how I am feeling.", dersReverseOptions),
            q("我在意自己的感受", "I care about what I am feeling.", dersReverseOptions),
            q("我对自己的感受感到困惑", "I am confused about how I feel.", dersOptions),
            q("当我难过时，我能承认自己的情绪", "When I'm upset, I acknowledge my emotions.", dersReverseOptions),
            q("当我难过时，我会因为自己有这种感受而生自己的气", "When I'm upset, I become angry with myself for feeling that way.", dersOptions),
            q("当我难过时，我会因为自己有这种感受而感到尴尬", "When I'm upset, I become embarrassed for feeling that way.", dersOptions),
            q("当我难过时，我很难完成工作", "When I'm upset, I have difficulty getting work done.", dersOptions),
            q("当我难过时，我会失控", "When I'm upset, I become out of control.", dersOptions),
            q("当我难过时，我相信自己会长时间保持这种状态", "When I'm upset, I believe that I will remain that way for a long time.", dersOptions),
            q("当我难过时，我相信自己最终会变得非常抑郁", "When I'm upset, I believe that I'll end up feeling very depressed.", dersOptions),
            q("当我难过时，我相信自己的感受是合理且重要的", "When I'm upset, I believe that my feelings are valid and important.", dersReverseOptions),
            q("当我难过时，我很难专注于其他事情", "When I'm upset, I have difficulty focusing on other things.", dersOptions),
            q("当我难过时，我感到失控", "When I'm upset, I feel out of control.", dersOptions),
            q("当我难过时，我仍然能完成任务", "When I'm upset, I can still get things done.", dersReverseOptions),
            q("当我难过时，我会因为自己有这种感受而感到羞耻", "When I'm upset, I feel ashamed with myself for feeling that way.", dersOptions),
            q("当我难过时，我知道自己最终能找到方法感觉好一些", "When I'm upset, I know that I can find a way to eventually feel better.", dersReverseOptions),
            q("当我难过时，我觉得自己很软弱", "When I'm upset, I feel like I am weak.", dersOptions),
            q("当我难过时，我觉得自己能控制自己的行为", "When I'm upset, I feel like I can remain in control of my behaviors.", dersReverseOptions),
            q("当我难过时，我会因为自己有这种感受而内疚", "When I'm upset, I feel guilty for feeling that way.", dersOptions),
            q("当我难过时，我很难集中注意力", "When I'm upset, I have difficulty concentrating.", dersOptions),
            q("当我难过时，我很难控制自己的行为", "When I'm upset, I have difficulty controlling my behaviors.", dersOptions),
            q("当我难过时，我相信自己做什么都无法让自己好受一些", "When I'm upset, I believe that there is nothing I can do to make myself feel better.", dersOptions),
            q("当我难过时，我会因为自己有这种感受而烦躁", "When I'm upset, I become irritated with myself for feeling that way.", dersOptions),
            q("当我难过时，我开始对自己感觉非常糟糕", "When I'm upset, I start to feel very bad about myself.", dersOptions),
            q("当我难过时，我相信自己只能沉溺其中", "When I'm upset, I believe that wallowing in it is all I can do.", dersOptions),
            q("当我难过时，我失去对行为的控制", "When I'm upset, I lose control over my behaviors.", dersOptions),
            q("当我难过时，我很难思考其他任何事情", "When I'm upset, I have difficulty thinking about anything else.", dersOptions),
            q("当我难过时，我会花时间弄清楚自己真正的感受", "When I'm upset, I take time to figure out what I'm really feeling.", dersReverseOptions),
            q("当我难过时，我需要很长时间才能感觉好一些", "When I'm upset, it takes me a long time to feel better.", dersOptions),
            q("当我难过时，我的情绪让我不堪重负", "When I'm upset, my emotions feel overwhelming.", dersOptions)
        ),
        bands = listOf(
            b(36, 84, "lower", "情绪调节困难较少", "Lower difficulties", "情绪调节困难相对较少。如果仍有明显情绪波动，可以继续记录触发情境。", "Emotion-regulation difficulties are relatively low. If mood swings still bother you, keep tracking triggers."),
            b(85, 132, "moderate", "中等程度情绪调节困难", "Moderate difficulties", "情绪调节困难处于中等水平。可以练习识别情绪、暂停和寻求支持。", "Emotion-regulation difficulties are moderate. Practice naming feelings, pausing, and seeking support."),
            b(133, 180, "higher", "较高程度情绪调节困难", "Higher difficulties", "情绪调节困难较明显，可能影响日常生活。建议与专业人员讨论，尤其是情绪失控或自伤念头出现时。", "Emotion-regulation difficulties are higher and may affect daily life. Discuss them with a professional, especially if emotions feel out of control or self-harm thoughts appear.")
        ),
        maxScoreOverride = 180,
        estimatedMinutes = 7,
        featured = false,
        source = "DERS: Gratz & Roemer (2004). 非商业临床与研究使用通常免费；商业或电子再分发前请与作者/出版方确认。",
        sourceEn = "DERS: Gratz & Roemer (2004). Usually free for non-commercial clinical and research use; confirm with the authors/publisher before commercial or electronic redistribution.",
        note = "DERS 没有公认的诊断界值，分数越高表示情绪调节困难越多；六个维度建议分别理解。",
        noteEn = "The DERS has no universally accepted diagnostic cutoff; higher scores indicate more emotion-regulation difficulty. Interpret the six facets separately."
    )

    private val shapsOptions = listOf(
        o("非常同意", "Strongly agree", 0),
        o("同意", "Agree", 0),
        o("不同意", "Disagree", 1),
        o("非常不同意", "Strongly disagree", 1)
    )

    val shaps = ScaleDef(
        type = "SHAPS",
        name = "快感缺失量表（SHAPS）",
        nameEn = "Snaith-Hamilton Pleasure Scale (SHAPS)",
        shortName = "SHAPS",
        category = "抑郁与焦虑",
        categoryEn = "Depression & anxiety",
        description = "14 项自评，了解体验快乐的能力；总分越高表示快感缺失越明显。",
        descriptionEn = "A 14-item measure of hedonic tone; higher scores indicate greater anhedonia.",
        timeframe = "目前",
        timeframeEn = "Right now",
        instruction = "请阅读每句话，选择你同意或不同意的程度。请按你最近的实际感受作答。",
        instructionEn = "Read each statement and choose how much you agree or disagree, based on how you have felt recently.",
        questions = listOf(
            q("我会享受自己最喜欢的电视或广播节目", "I would enjoy my favourite television or radio programme.", shapsOptions),
            q("我会享受与家人或亲密朋友在一起", "I would enjoy being with my family or close friends.", shapsOptions),
            q("我会从自己的爱好和消遣中找到乐趣", "I would find pleasure in my hobbies and pastimes.", shapsOptions),
            q("我能享受自己最喜欢的一餐", "I would be able to enjoy my favourite meal.", shapsOptions),
            q("我会享受一个热水澡或清爽的淋浴", "I would enjoy a warm bath or refreshing shower.", shapsOptions),
            q("我会从花香、海边清新的空气或新烤面包的气味中找到乐趣", "I would find pleasure in the scent of flowers or the smell of a fresh sea breeze or freshly baked bread.", shapsOptions),
            q("我会享受看到别人微笑的脸", "I would enjoy seeing other people's smiling faces.", shapsOptions),
            q("当我用心打扮后，我会享受自己看起来精神的样子", "I would enjoy looking smart when I have made an effort with my appearance.", shapsOptions),
            q("我会享受读书、杂志或报纸", "I would enjoy reading a book, magazine or newspaper.", shapsOptions),
            q("我会享受一杯茶、咖啡或我最喜欢的饮料", "I would enjoy a cup of tea or coffee or my favorite drink.", shapsOptions),
            q("我会从小事中找到乐趣，比如晴朗的阳光、朋友打来的电话", "I would find pleasure in small things, e.g. bright sunny day, a telephone call from a friend.", shapsOptions),
            q("我能享受美丽的风景", "I would be able to enjoy a beautiful landscape or view.", shapsOptions),
            q("我会从帮助别人中获得乐趣", "I would get pleasure from helping others.", shapsOptions),
            q("当我得到别人的称赞时，我会感到愉快", "I would feel pleasure when I receive praise from other people.", shapsOptions)
        ),
        bands = listOf(
            b(0, 2, "normal", "没有明显快感缺失", "No clear anhedonia", "你目前仍能从日常事物中体验到快乐。", "You can still experience pleasure from everyday things."),
            b(3, 5, "mild", "可能存在轻度快感缺失", "Possible mild anhedonia", "得分达到常见界值，可能有轻度快感缺失。可以观察它是否持续两周以上。", "The score reaches the common threshold, suggesting possible mild anhedonia. Notice whether it lasts more than two weeks."),
            b(6, 14, "significant", "快感缺失较明显", "Significant anhedonia", "体验快乐的能力明显下降。建议与专业人员讨论，尤其是同时存在情绪低落或兴趣丧失时。", "The ability to experience pleasure is noticeably reduced. Discuss it with a professional, especially alongside low mood or loss of interest.")
        ),
        maxScoreOverride = 14,
        estimatedMinutes = 3,
        featured = false,
        source = "SHAPS: Snaith et al. (1995). 个人使用与研究可复制；商业用途需与 British Journal of Psychiatry 协商。",
        sourceEn = "SHAPS: Snaith et al. (1995). Reproduction for personal use and research is permitted; commercial use must be negotiated with the British Journal of Psychiatry.",
        note = "3 分及以上常被视为快感缺失的参考界值；分数不是抑郁症的诊断。",
        noteEn = "A score of 3 or more is commonly used as a reference threshold for anhedonia; this is not a diagnosis of depression."
    )

    private val asrsFreq = listOf(o("从不", "Never", 0), o("很少", "Rarely", 1), o("有时", "Sometimes", 2), o("经常", "Often", 3), o("非常频繁", "Very often", 4))

    val asrs18 = ScaleDef(
        type = "ASRS18",
        name = "成人 ADHD 症状清单（ASRS-18）",
        nameEn = "Adult ADHD Self-Report Scale (ASRS-18)",
        shortName = "ASRS-18",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "18 项完整症状清单，A 部分（注意力不集中）与 B 部分（冲动/多动）各 9 题，分别计算 0–36 分，分级取较高的一部分。",
        descriptionEn = "The full 18-item symptom checklist with 9 inattention items (Part A) and 9 hyperactivity-impulsivity items (Part B), each scored 0–36; the higher part determines the band.",
        timeframe = "过去六个月",
        timeframeEn = "Over the past six months",
        instruction = "请按过去六个月的情况选择频率。A、B 两部分分别把频率分数相加（0–4 分），不是统计「经常」的条目数。",
        instructionEn = "Rate the past six months. Sum the 0–4 frequency scores within Part A and Part B separately; do not count the number of often responses.",
        questions = listOf(
            q("当必须进行一件枯燥或困难的计划时，你会多常粗心犯错？", "How often do you make careless mistakes when you have to work on a boring or difficult project?", asrsFreq),
            q("当正在做枯燥或重复性的工作时，你多常有持续专注的困难？", "How often do you have difficulty keeping your attention when you are doing boring or repetitive work?", asrsFreq),
            q("即使有人直接对你说话，你会多常有困难专注于别人跟你讲话的内容？", "How often do you have difficulty concentrating on what people say to you, even when they are speaking to you directly?", asrsFreq),
            q("一旦完成任何计划中最具挑战的部分之后，你多常有完成计划最后细节的困难？", "How often do you have trouble wrapping up the final details of a project, once the challenging parts have been done?", asrsFreq),
            q("当必须从事需要有组织规划性的任务时，你会多常有困难井然有序地去做？", "How often do you have difficulty getting things in order when you have to do a task that requires organization?", asrsFreq),
            q("当有一件需要多费心思考的工作时，你会多常逃避或是延后开始去做？", "When you have a task that requires a lot of thought, how often do you avoid or delay getting started?", asrsFreq),
            q("在家里或是在工作时，你会多常没有把东西放对地方或是找不到东西？", "How often do you misplace or have difficulty finding things at home or at work?", asrsFreq),
            q("你会多常因身旁的活动或声音而分心？", "How often are you distracted by activity or noise around you?", asrsFreq),
            q("你会多常有困难去记得约会或是必须要做的事？", "How often do you have problems remembering appointments or obligations?", asrsFreq),
            q("当你必须长时间坐着时，你会多常坐不安稳或扭动手脚？", "How often do you fidget or squirm with your hands or feet when you have to sit down for a long time?", asrsFreq),
            q("你会多常在开会时或在其他被期待坐好的场合中离开座位？", "How often do you leave your seat in meetings or other situations in which you are expected to remain seated?", asrsFreq),
            q("你会多常觉得静不下来或烦躁不安？", "How often do you feel restless or fidgety?", asrsFreq),
            q("当有自己独处的时间时，你会多常有觉得困难使自己平静和放松？", "How often do you have difficulty unwinding and relaxing when you have time to yourself?", asrsFreq),
            q("你会多常像被马达所驱动一样，觉得自己过度地活跃，不得不做事？", "How often do you feel overly active and compelled to do things, like you were driven by a motor?", asrsFreq),
            q("在社交场合中，你会多常发现自己话讲得太多？", "How often do you find yourself talking too much when you are in social situations?", asrsFreq),
            q("当与他人交谈时，你会多常在别人还没把话讲完前就插嘴或接话替对方把话讲完？", "When you're in a conversation, how often do you find yourself finishing the sentences of the people you are talking to, before they can finish them themselves?", asrsFreq),
            q("在需要轮流排队的场合时，你会多常有困难轮流等待？", "How often do you have difficulty waiting your turn in situations when turn taking is required?", asrsFreq),
            q("你会多常在别人忙碌时打断别人？", "How often do you interrupt others when they are busy?", asrsFreq),
        ),
        bands = listOf(
            b(0, 16, "unlikely", "不太可能达到 ADHD 筛查标准", "Unlikely", "A、B 两部分的分数都低于 17 分。如果仍然困扰你，可以把具体困难告诉医生或专业人员。", "Both Part A and Part B scored below 17. If these difficulties still trouble you, discuss them with a doctor or professional."),
            b(17, 23, "likely", "可能有 ADHD 症状", "Likely", "较高的一部分达到 17–23 分，症状可能与 ADHD 一致。建议由专业人员进一步评估；这不是诊断。", "The higher part scored 17–23, suggesting symptoms consistent with ADHD. Further professional assessment is suggested; this is not a diagnosis."),
            b(24, 36, "very_likely", "非常可能有 ADHD 症状", "Very likely", "较高的一部分达到 24 分及以上，症状与 ADHD 高度一致。建议尽快由专业人员评估，并同时了解儿童期起病与功能受损情况。", "The higher part scored 24 or above, suggesting symptoms highly consistent with ADHD. Seek professional assessment soon, including childhood onset and impairment."),
            // 旧版「计数法」记录的历史展示，不再用于新的测评
            b(0, 18, "below", "旧版计分：未达到计数阈值", "Legacy scoring: below threshold", "这是旧版 ASRS-18 的计数结果，仅作历史参考；请使用新版重新测评。", "This is a legacy count-based ASRS-18 result for historical reference only; retake the updated version."),
            b(0, 36, "adhd_i", "可能患有 ADHD-I（注意力缺陷为主型）", "Possible ADHD-I (predominantly inattentive)", "A 部分达到 17 分及以上、B 部分低于 17 分，提示可能为注意力缺陷为主型。建议由专业人员进一步评估；这不是诊断。", "Part A scored 17 or above while Part B was below 17, suggesting the predominantly inattentive presentation. Further professional assessment is suggested; this is not a diagnosis."),
            b(0, 36, "adhd_h", "可能患有 ADHD-H（多动/冲动为主型）", "Possible ADHD-H (predominantly hyperactive-impulsive)", "B 部分达到 17 分及以上、A 部分低于 17 分，提示可能为多动/冲动为主型。建议由专业人员进一步评估；这不是诊断。", "Part B scored 17 or above while Part A was below 17, suggesting the predominantly hyperactive-impulsive presentation. Further professional assessment is suggested; this is not a diagnosis."),
            b(0, 36, "adhd_c", "可能患有 ADHD-C（混合型）", "Possible ADHD-C (combined)", "A、B 两部分都达到 17 分及以上，提示可能为混合型。建议由专业人员进一步评估；这不是诊断。", "Both Part A and Part B scored 17 or above, suggesting the combined presentation. Further professional assessment is suggested; this is not a diagnosis.")
        ),
        scoring = ScaleScoring.ASRS18,
        maxScoreOverride = 36,
        estimatedMinutes = 5,
        featured = false,
        source = "ASRS v1.1 症状清单：Kessler et al. (2005) / World Health Organization；中文版参考 ZI/Harvard 公开的台湾繁体版。可免费用于临床与研究，请保留来源。",
        sourceEn = "ASRS v1.1 Symptom Checklist: Kessler et al. (2005) / World Health Organization; Chinese wording follows the publicly available Taiwan traditional-Chinese form. Free for clinical and research use with attribution.",
        note = "A、B 两部分各 9 题、各 0–36 分，单部分 0–16 不太可能、17–23 很可能、24+ 非常可能；两部分都 ≥17 提示混合型，仅一部分 ≥17 提示对应亚型。亚型只是筛查提示，确诊需结合儿童期起病、功能受损与临床访谈。",
        noteEn = "Part A and Part B each have 9 items and 0–36 points; 0–16 unlikely, 17–23 likely, 24 or above very likely within a part. Both parts 17 or above suggest the combined presentation, while only one part 17+ suggests that subtype. Subtype is a screening hint only; diagnosis needs childhood onset, impairment and a clinical interview."
    )

    val all: List<ScaleDef> = listOf(
        phq9,
        gad7,
        phq2,
        gad2,
        phq15,
        mdq,
        asrm,
        ocir,
        pcptsd5,
        pcl5,
        isi,
        who5,
        whodas,
        auditc,
        itq,
        asrs5,
        scoff,
        epds,
        dast10,
        dass21,
        psqi,
        pss4,
        rosenberg,
        aq10,
        asrs18,
        pss10,
        stopbang,
        audit,
        ftnd,
        ucla3,
        wsas,
        swls,
        k10,
        eat26,
        pgsi,
        crafft,
        des2,
        asq,
        bsl23,
        iesr,
        spin,
        cssrs,
        pq16,
        ess,
        mspss,
        ffmq39,
        ders36,
        shaps
    )

    val featured: List<ScaleDef> = all.filter { it.featured }

    fun byType(type: String): ScaleDef? = all.firstOrNull { it.type == type }

    fun categories(english: Boolean): List<String> =
        all.map { it.categoryFor(english) }.distinct()

    fun inCategory(category: String, english: Boolean): List<ScaleDef> =
        all.filter { it.categoryFor(english) == category }
}
