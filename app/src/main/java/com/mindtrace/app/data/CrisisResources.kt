package com.mindtrace.app.data

/** 各地紧急电话与心理援助热线。默认中国；用户可在紧急支持页切换地区。 */
data class CrisisRegion(
    val code: String,
    val nameZh: String,
    val nameEn: String,
    val emergency: String,
    val police: String,
    val hotline: String?,
    val hotlineLabelZh: String,
    val hotlineLabelEn: String
)

object CrisisDirectory {
    val regions = listOf(
        CrisisRegion("CN", "中国大陆", "Mainland China", "120", "110", "12356", "全国心理援助热线", "National mental health hotline"),
        CrisisRegion("HK", "中国香港", "Hong Kong", "999", "999", "2382 0000", "撒玛利亚防止自杀会", "The Samaritans"),
        CrisisRegion("TW", "中国台湾", "Taiwan", "119", "110", "1925", "安心专线", "Anxin hotline"),
        CrisisRegion("MO", "中国澳门", "Macau", "999", "999", null, "", ""),
        CrisisRegion("US", "美国", "United States", "911", "911", "988", "Suicide & Crisis Lifeline", "Suicide & Crisis Lifeline"),
        CrisisRegion("CA", "加拿大", "Canada", "911", "911", "988", "Suicide Crisis Helpline", "Suicide Crisis Helpline"),
        CrisisRegion("GB", "英国", "United Kingdom", "999", "999", "116 123", "Samaritans", "Samaritans"),
        CrisisRegion("IE", "爱尔兰", "Ireland", "112", "999", "116 123", "Samaritans", "Samaritans"),
        CrisisRegion("DE", "德国", "Germany", "112", "110", "0800 111 0 111", "Telefonseelsorge", "Telefonseelsorge"),
        CrisisRegion("FR", "法国", "France", "112", "17", "3114", "Numéro national de prévention du suicide", "National suicide prevention line"),
        CrisisRegion("AU", "澳大利亚", "Australia", "000", "000", "13 11 14", "Lifeline", "Lifeline"),
        CrisisRegion("NZ", "新西兰", "New Zealand", "111", "111", "1737", "Need to Talk?", "Need to Talk?"),
        CrisisRegion("JP", "日本", "Japan", "119", "110", "0570-064-556", "いのちの電話", "Inochi no Denwa"),
        CrisisRegion("KR", "韩国", "South Korea", "119", "112", "109", "자살예방상담전화", "Suicide prevention hotline"),
        CrisisRegion("SG", "新加坡", "Singapore", "995", "999", "1767", "Samaritans of Singapore", "Samaritans of Singapore"),
        CrisisRegion("IN", "印度", "India", "112", "112", "9152987821", "iCall", "iCall"),
        CrisisRegion("EU", "欧盟/通用", "EU / generic", "112", "112", null, "", ""),
        CrisisRegion("OTHER", "其他地区", "Other region", "112", "112", null, "", "")
    )

    fun forCode(code: String?): CrisisRegion =
        regions.firstOrNull { it.code == code } ?: regions.first()
}
