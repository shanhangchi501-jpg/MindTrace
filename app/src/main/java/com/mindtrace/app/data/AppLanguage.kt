package com.mindtrace.app.data

import java.util.Locale

enum class AppLanguage(val code: String) {
    ZH("zh"),
    EN("en");

    val isEnglish: Boolean get() = this == EN
}

object AppLocale {
    @Volatile
    var current: AppLanguage = AppLanguage.ZH

    fun fromCode(code: String?): AppLanguage = when (code) {
        "en" -> AppLanguage.EN
        "zh" -> AppLanguage.ZH
        else -> if (Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("zh")) {
            AppLanguage.ZH
        } else {
            AppLanguage.EN
        }
    }
}
