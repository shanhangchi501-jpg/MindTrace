package com.mindtrace.app.data

data class ConditionDef(
    val key: String,
    val label: String,
    val hint: String,
    val suggested: List<String>
)

data class SymptomDef(
    val id: String,
    val name: String,
    val category: String,
    val conditionTags: List<String>
)

object SymptomCatalog {

    val conditions: List<ConditionDef> = listOf(
        ConditionDef(
            key = "depression",
            label = "抑郁低落",
            hint = "情绪、兴趣、精力与睡眠",
            suggested = listOf(
                "low_mood", "anhedonia", "fatigue", "insomnia", "early_wake",
                "appetite_loss", "concentration", "guilt", "hopelessness",
                "withdrawal", "crying"
            )
        ),
        ConditionDef(
            key = "anxiety",
            label = "焦虑紧张",
            hint = "担忧、惊恐与身体反应",
            suggested = listOf(
                "anxiety", "fear", "panic", "palpitations", "breathless",
                "tension", "insomnia", "avoidance", "dizziness", "gi"
            )
        ),
        ConditionDef(
            key = "bipolar",
            label = "情绪波动 / 双相",
            hint = "低落与高涨的起伏、睡眠需求变化",
            suggested = listOf(
                "mood_swing", "low_mood", "euphoria", "energy_surge",
                "hyperactivity", "sleep_decrease", "racing", "impulsivity",
                "irritability", "concentration"
            )
        ),
        ConditionDef(
            key = "ocd",
            label = "强迫困扰",
            hint = "反复出现的想法与行为",
            suggested = listOf(
                "obsession", "compulsion", "anxiety", "avoidance",
                "intrusive", "tension", "concentration"
            )
        ),
        ConditionDef(
            key = "psychosis",
            label = "精神病性体验",
            hint = "幻觉、多疑、思维与感知变化",
            suggested = listOf(
                "hallucination", "delusion", "paranoia", "disorganized_speech",
                "disorganized_behavior", "catatonia", "flat_affect", "alogia",
                "avolition", "self_neglect", "withdrawal", "concentration",
                "agitation", "sleep_decrease", "numbness"
            )
        ),
        ConditionDef(
            key = "schizophrenia",
            label = "精神分裂症谱系",
            hint = "阳性症状、阴性症状与认知变化",
            suggested = listOf(
                "hallucination", "delusion", "paranoia", "disorganized_speech",
                "disorganized_behavior", "catatonia", "passivity", "flat_affect",
                "alogia", "avolition", "anhedonia", "withdrawal", "self_neglect",
                "concentration", "memory", "sleep_decrease", "agitation"
            )
        ),
        ConditionDef(
            key = "trauma",
            label = "创伤与应激",
            hint = "闪回、警觉与回避",
            suggested = listOf(
                "flashback", "nightmare", "avoidance", "numbness",
                "dissociation", "hypervigilance", "insomnia", "guilt",
                "irritability"
            )
        ),
        ConditionDef(
            key = "sleep",
            label = "睡眠问题",
            hint = "入睡、早醒与睡眠节律",
            suggested = listOf(
                "insomnia", "early_wake", "hypersomnia", "nightmare",
                "fatigue", "concentration", "irritability"
            )
        ),
        ConditionDef(
            key = "unsure",
            label = "还不确定，先记录",
            hint = "先选一些最贴近的感受，之后随时可改",
            suggested = listOf(
                "low_mood", "anhedonia", "anxiety", "irritability",
                "insomnia", "fatigue", "concentration", "withdrawal"
            )
        )
    )

    val symptoms: List<SymptomDef> = listOf(
        // 情绪
        SymptomDef("low_mood", "情绪低落", "情绪", listOf("depression", "bipolar", "unsure")),
        SymptomDef("anhedonia", "兴趣减退", "情绪", listOf("depression", "bipolar", "unsure")),
        SymptomDef("anxiety", "紧张不安", "情绪", listOf("anxiety", "ocd", "unsure")),
        SymptomDef("irritability", "易怒", "情绪", listOf("depression", "bipolar", "anxiety", "trauma", "sleep")),
        SymptomDef("mood_swing", "情绪大起大落", "情绪", listOf("bipolar")),
        SymptomDef("emptiness", "空虚感", "情绪", listOf("depression", "trauma")),
        SymptomDef("guilt", "自责内疚", "情绪", listOf("depression", "trauma")),
        SymptomDef("hopelessness", "绝望感", "情绪", listOf("depression")),
        SymptomDef("fear", "莫名恐惧", "情绪", listOf("anxiety", "trauma")),
        SymptomDef("panic", "惊恐发作", "情绪", listOf("anxiety")),
        SymptomDef("numbness", "情感麻木", "情绪", listOf("trauma", "depression", "psychosis")),
        SymptomDef("euphoria", "异常兴奋愉悦", "情绪", listOf("bipolar")),
        // 身体
        SymptomDef("insomnia", "入睡困难", "身体", listOf("depression", "anxiety", "bipolar", "sleep", "trauma", "unsure")),
        SymptomDef("early_wake", "早醒", "身体", listOf("depression", "sleep")),
        SymptomDef("hypersomnia", "睡眠过多", "身体", listOf("depression", "sleep")),
        SymptomDef("sleep_decrease", "睡眠需求明显减少", "身体", listOf("bipolar", "psychosis")),
        SymptomDef("nightmare", "噩梦", "身体", listOf("trauma", "sleep")),
        SymptomDef("fatigue", "疲乏无力", "身体", listOf("depression", "bipolar", "sleep", "unsure")),
        SymptomDef("appetite_loss", "食欲下降", "身体", listOf("depression")),
        SymptomDef("appetite_up", "食欲增加", "身体", listOf("depression", "bipolar")),
        SymptomDef("weight_change", "体重明显变化", "身体", listOf("depression", "bipolar")),
        SymptomDef("headache", "头痛", "身体", listOf("anxiety", "depression")),
        SymptomDef("palpitations", "心慌 / 心悸", "身体", listOf("anxiety")),
        SymptomDef("breathless", "呼吸不畅", "身体", listOf("anxiety")),
        SymptomDef("pain", "身体疼痛", "身体", listOf("depression", "anxiety")),
        SymptomDef("gi", "肠胃不适", "身体", listOf("anxiety", "depression")),
        SymptomDef("dizziness", "头晕", "身体", listOf("anxiety")),
        SymptomDef("sexual_change", "性欲改变", "身体", listOf("depression", "bipolar")),
        SymptomDef("tension", "肌肉紧绷", "身体", listOf("anxiety", "ocd")),
        // 思维
        SymptomDef("concentration", "注意力不集中", "思维", listOf("depression", "anxiety", "bipolar", "ocd", "psychosis", "sleep", "unsure")),
        SymptomDef("memory", "记忆力下降", "思维", listOf("depression", "anxiety")),
        SymptomDef("indecision", "难以做决定", "思维", listOf("depression", "anxiety")),
        SymptomDef("racing", "思绪奔涌", "思维", listOf("bipolar", "anxiety")),
        SymptomDef("slow", "思维迟缓", "思维", listOf("depression")),
        SymptomDef("worthless", "无价值感", "思维", listOf("depression")),
        SymptomDef("intrusive", "侵入性想法", "思维", listOf("ocd", "trauma", "anxiety")),
        SymptomDef("obsession", "反复的强迫思维", "思维", listOf("ocd")),
        SymptomDef("compulsion", "反复检查 / 清洗等行为", "行为", listOf("ocd")),
        SymptomDef("hallucination", "幻觉", "阳性症状", listOf("psychosis", "schizophrenia", "bipolar")),
        SymptomDef("delusion", "坚信不寻常的事", "阳性症状", listOf("psychosis", "schizophrenia", "bipolar")),
        SymptomDef("paranoia", "多疑 / 被针对感", "阳性症状", listOf("psychosis", "schizophrenia", "bipolar")),
        SymptomDef("disorganized_speech", "思维紊乱 / 言语散乱", "阳性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("disorganized_behavior", "行为紊乱", "阳性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("catatonia", "紧张症表现", "阳性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("passivity", "被控制感 / 思维被洞悉", "阳性症状", listOf("schizophrenia")),
        // 阴性症状
        SymptomDef("flat_affect", "情感平淡", "阴性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("alogia", "言语贫乏", "阴性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("avolition", "意志减退", "阴性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("self_neglect", "个人卫生下降", "阴性症状", listOf("psychosis", "schizophrenia")),
        SymptomDef("dissociation", "解离 / 不真实感", "思维", listOf("trauma", "anxiety")),
        SymptomDef("flashback", "创伤闪回", "思维", listOf("trauma")),
        SymptomDef("hypervigilance", "过度警觉", "思维", listOf("trauma", "anxiety")),
        // 行为
        SymptomDef("avoidance", "回避社交或场合", "行为", listOf("anxiety", "ocd", "trauma")),
        SymptomDef("withdrawal", "社交退缩", "行为", listOf("depression", "psychosis", "unsure")),
        SymptomDef("crying", "容易哭泣", "行为", listOf("depression")),
        SymptomDef("agitation", "坐立不安", "行为", listOf("anxiety", "bipolar", "psychosis")),
        SymptomDef("impulsivity", "冲动行为", "行为", listOf("bipolar", "trauma")),
        SymptomDef("self_harm", "自伤行为", "行为", listOf("depression", "bipolar")),
        SymptomDef("hyperactivity", "话多 / 活动明显增多", "行为", listOf("bipolar")),
        SymptomDef("slowness", "行动迟缓", "行为", listOf("depression")),
        SymptomDef("alcohol", "酒精 / 物质使用", "行为", listOf("depression", "anxiety", "bipolar")),
        SymptomDef("spending", "冲动消费 / 赌博", "行为", listOf("bipolar"))
    )

    val triggers: List<String> = listOf(
        "工作 / 学习压力",
        "人际冲突",
        "家庭矛盾",
        "睡眠不足",
        "身体不适",
        "药物调整",
        "经期 / 激素变化",
        "季节 / 天气",
        "孤独 / 独处",
        "经济压力",
        "创伤回忆",
        "重要事件",
        "其他"
    )

    fun suggestedFor(conditionKeys: Collection<String>): Set<String> {
        if (conditionKeys.isEmpty()) return conditions.last().suggested.toSet()
        return conditions
            .filter { it.key in conditionKeys }
            .flatMap { it.suggested }
            .toSet()
    }

    fun byId(id: String): SymptomDef? = symptoms.firstOrNull { it.id == id }

    fun seedEntities(enabledIds: Set<String>): List<SymptomEntity> =
        symptoms.mapIndexed { index, def ->
            SymptomEntity(
                id = def.id,
                name = def.name,
                category = def.category,
                conditionTags = def.conditionTags.joinToString(","),
                isCustom = false,
                enabled = def.id in enabledIds,
                sortOrder = index
            )
        }
}
