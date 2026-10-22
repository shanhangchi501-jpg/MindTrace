package com.mindtrace.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 科普内容：
 * - 疾病与症状条目来自「多意识体系统 Wiki」；
 * - 药物条目来自维基百科。
 * 两者均按 CC BY-SA 4.0 许可随应用离线分发。
 *
 * 正文在构建时转换为结构化块，表格可以原生渲染；应用本身仍然不联网。
 */
@Serializable
data class EducationBlock(
    val type: String = "paragraph",
    val text: String = "",
    val title: String = "",
    val level: Int = 0,
    val items: List<String> = emptyList(),
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

@Serializable
data class EducationEntry(
    val id: String,
    val category: String,
    val title: String,
    val titleEn: String = "",
    val description: String = "",
    val descriptionEn: String = "",
    val blocks: List<EducationBlock> = emptyList(),
    val blocksEn: List<EducationBlock> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val source: String = "",
    val sourceUrl: String = "",
    val sourceUrlEn: String = "",
    val license: String = "CC BY-SA 4.0",
    val updated: String = "",
    val truncated: Boolean = false,
    val topic: String = ""
) {
    fun displayTitle(english: Boolean): String =
        if (english && titleEn.isNotBlank()) titleEn else title

    fun displayDescription(english: Boolean): String =
        if (english && descriptionEn.isNotBlank()) descriptionEn else description

    /** 药物条目有英文正文；Wiki 条目目前只有中文正文。 */
    fun displayBlocks(english: Boolean): List<EducationBlock> =
        if (english && blocksEn.isNotEmpty()) blocksEn else blocks

    fun hasEnglishContent(): Boolean = blocksEn.isNotEmpty()

    fun hasTable(english: Boolean): Boolean =
        displayBlocks(english).any { it.type == "table" }

    fun sourceUrlFor(english: Boolean): String =
        if (english && sourceUrlEn.isNotBlank()) sourceUrlEn else sourceUrl

    fun sourceLabel(): String =
        if (source.contains("多意识体") || source.contains("MPS", ignoreCase = true)) "MPS Wiki"
        else "Wikipedia"
}

@Serializable
data class EducationSource(
    val name: String = "",
    val url: String = "",
    val license: String = ""
)

@Serializable
data class EducationData(
    val version: Int = 3,
    val sources: List<EducationSource> = emptyList(),
    val disclaimerZh: String = "",
    val disclaimerEn: String = "",
    val entries: List<EducationEntry> = emptyList()
) {
    fun disclaimer(english: Boolean): String =
        if (english) disclaimerEn else disclaimerZh
}

object EducationCatalog {

    val categories = listOf(
        "全部",
        "疾病",
        "症状",
        "药物"
    )

    @Volatile
    private var cached: EducationData? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): EducationData {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val text = context.assets.open("education.json")
                    .bufferedReader()
                    .use { it.readText() }
                json.decodeFromString<EducationData>(text).also { cached = it }
            }
        }
    }

    fun categoryLabel(category: String, english: Boolean): String = when (category) {
        "全部" -> if (english) "All" else "全部"
        "疾病" -> if (english) "Conditions" else "疾病"
        "症状" -> if (english) "Symptoms" else "症状"
        "药物" -> if (english) "Medications" else "药物"
        else -> category
    }
}
