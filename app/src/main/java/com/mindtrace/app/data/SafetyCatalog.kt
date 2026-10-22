package com.mindtrace.app.data

data class SafetyLevel(val value: Int, val label: String, val hint: String)

data class CrisisResource(
    val name: String,
    val number: String,
    val note: String,
    val kind: String
)

object SafetyCatalog {

    val levels: List<SafetyLevel> = listOf(
        SafetyLevel(0, "没有", "今天没有出现"),
        SafetyLevel(1, "一闪而过", "出现过，但很快过去了"),
        SafetyLevel(2, "有过想法", "有念头，但没有具体计划"),
        SafetyLevel(3, "有具体计划", "想过具体的方式或时间"),
        SafetyLevel(4, "有实施打算", "近期有采取行动的打算"),
        SafetyLevel(5, "最近有过行动", "近期发生过伤害自己的行为")
    )

    val crisisResources: List<CrisisResource> = listOf(
        CrisisResource(
            name = "全国心理援助热线",
            number = "12356",
            note = "国家卫健委统一号码，各地接听时间以当地为准",
            kind = "hotline"
        ),
        CrisisResource(
            name = "急救",
            number = "120",
            note = "如果已经受伤、服药过量或处于紧急危险中",
            kind = "emergency"
        ),
        CrisisResource(
            name = "报警",
            number = "110",
            note = "需要即时人身安全保护时",
            kind = "emergency"
        )
    )

    val groundingSteps: List<String> = listOf(
        "说出你能看到的 5 样东西",
        "触摸你能感觉到的 4 样东西",
        "听见周围的 3 种声音",
        "闻到或想到 2 种气味",
        "慢慢呼吸，注意 1 次吸气和呼气"
    )

    val safetyPlanPrompts: List<String> = listOf(
        "哪些迹象说明我正在变得更糟？",
        "过去哪些小事曾帮我撑过一阵？",
        "我可以联系谁？",
        "哪些地方或物品需要暂时远离？",
        "让我愿意再等等的理由是什么？"
    )
}
