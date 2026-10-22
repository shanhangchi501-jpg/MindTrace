package com.mindtrace.app.data

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
    DASS21
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
    val selfHarmQuestionIndex: Int? = null
) {
    val maxScore: Int
        get() = maxScoreOverride
            ?: questions.sumOf { q -> q.options.maxOfOrNull { it.value } ?: 0 }

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
        else -> answers.indices.sumOf { index ->
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
            "AUDITC" -> if (english) {
                "Cut-offs vary by sex: 3+ for women, 4+ for men. This is a screening result, not a diagnosis."
            } else {
                "不同性别的参考界值不同：女性 3 分及以上、男性 4 分及以上。此结果仅用于筛查，不是诊断。"
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

    private fun psqiScore(answers: List<Int>): Int {
        val quality = optionValue(17, answers.getOrElse(17) { 0 })
        val latency = optionValue(1, answers.getOrElse(1) { 0 }).coerceIn(0, 3)
        val duration = optionValue(3, answers.getOrElse(3) { 0 }).coerceIn(0, 3)
        val disturbances = (5..13).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
        val disturbanceComponent = when {
            disturbances == 0 -> 0
            disturbances <= 9 -> 1
            disturbances <= 18 -> 2
            else -> 3
        }
        val medication = optionValue(14, answers.getOrElse(14) { 0 }).coerceIn(0, 3)
        val daytime = (15..16).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) } +
            optionValue(18, answers.getOrElse(18) { 0 })
        val daytimeComponent = when {
            daytime == 0 -> 0
            daytime <= 3 -> 1
            daytime <= 6 -> 2
            else -> 3
        }
        return quality + latency + duration + duration + disturbanceComponent +
            medication + daytimeComponent
    }

    private fun psqiDetail(answers: List<Int>, english: Boolean): String {
        val quality = optionValue(17, answers.getOrElse(17) { 0 })
        val latency = optionValue(1, answers.getOrElse(1) { 0 }).coerceIn(0, 3)
        val duration = optionValue(3, answers.getOrElse(3) { 0 }).coerceIn(0, 3)
        val disturbances = (5..13).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) }
        val disturbanceComponent = when {
            disturbances == 0 -> 0
            disturbances <= 9 -> 1
            disturbances <= 18 -> 2
            else -> 3
        }
        val medication = optionValue(14, answers.getOrElse(14) { 0 }).coerceIn(0, 3)
        val daytimeRaw = (15..16).sumOf { optionValue(it, answers.getOrElse(it) { 0 }) } +
            optionValue(18, answers.getOrElse(18) { 0 })
        val daytime = when {
            daytimeRaw == 0 -> 0
            daytimeRaw <= 3 -> 1
            daytimeRaw <= 6 -> 2
            else -> 3
        }
        return if (english) {
            "Quality $quality · Latency $latency · Duration $duration · Disturbance $disturbanceComponent · " +
                "Medication $medication · Daytime $daytime (each 0–3)"
        } else {
            "主观质量 $quality · 入睡潜伏期 $latency · 睡眠时长 $duration · 睡眠紊乱 $disturbanceComponent · " +
                "助眠药物 $medication · 日间功能 $daytime（各 0–3 分）"
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

    private val psqiTimeOptions = listOf(
        o("21:00 之前", "Before 21:00", 0),
        o("21:00–22:00", "21:00–22:00", 0),
        o("22:00–23:00", "22:00–23:00", 0),
        o("23:00–24:00", "23:00–24:00", 0),
        o("00:00 之后", "After 00:00", 0)
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
        name = "成人 ADHD 自评（ASRS-5）",
        nameEn = "Adult ADHD Self-Report Screening Scale (ASRS-5)",
        shortName = "ASRS-5",
        category = "注意力与其他",
        categoryEn = "Attention & other",
        description = "6 项成人注意力缺陷/多动筛查。",
        descriptionEn = "A 6-item adult ADHD screen.",
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
        name = "匹兹堡睡眠质量指数（PSQI，简化版）",
        nameEn = "Pittsburgh Sleep Quality Index (PSQI, simplified)",
        shortName = "PSQI",
        category = "睡眠",
        categoryEn = "Sleep",
        description = "19 项自评，了解过去一个月的睡眠质量。",
        descriptionEn = "A 19-item self-report of sleep quality over the past month.",
        timeframe = "过去一个月",
        timeframeEn = "Over the past month",
        instruction = "以下问题关于过去一个月的睡眠习惯。请选择最接近的情况。",
        instructionEn = "The following questions are about your sleep habits over the past month. Choose the closest answer.",
        questions = listOf(
            q("你通常几点上床睡觉？", "What time have you usually gone to bed?", psqiTimeOptions),
            q("你通常需要多久才能入睡？", "How long has it usually taken you to fall asleep?", psqiLatencyOptions),
            q("你通常几点起床？", "What time have you usually gotten up?", listOf(o("05:00 之前", "Before 05:00", 0), o("05:00–06:00", "05:00–06:00", 0), o("06:00–07:00", "06:00–07:00", 0), o("07:00–08:00", "07:00–08:00", 0), o("08:00 之后", "After 08:00", 0))),
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
            q("整体睡眠质量", "Overall sleep quality", listOf(o("很好", "Very good", 0), o("较好", "Fairly good", 1), o("较差", "Fairly bad", 2), o("很差", "Very bad", 3))),
            q("白天感到疲倦或精力不足", "How often have you felt tired or low in energy during the day?", psqiFreqOptions)
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
        note = "这是简化自评版，未计算完整睡眠效率；结果仅用于记录与就诊沟通。",
        noteEn = "This is a simplified self-report version and does not calculate full sleep efficiency; use it for tracking and clinical communication only."
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
        aq10
    )

    val featured: List<ScaleDef> = all.filter { it.featured }

    fun byType(type: String): ScaleDef? = all.firstOrNull { it.type == type }

    fun categories(english: Boolean): List<String> =
        all.map { it.categoryFor(english) }.distinct()

    fun inCategory(category: String, english: Boolean): List<ScaleDef> =
        all.filter { it.categoryFor(english) == category }
}
