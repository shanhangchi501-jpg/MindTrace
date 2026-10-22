package com.mindtrace.app

import com.mindtrace.app.data.AppLanguage
import com.mindtrace.app.data.DiagnosisCatalog
import com.mindtrace.app.ui.i18n.translate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisCatalogTest {

    @Test
    fun catalogs_are_not_empty_and_codes_are_unique_within_standard() {
        assertTrue(DiagnosisCatalog.icd10.size > 50)
        assertTrue(DiagnosisCatalog.icd11.size > 50)
        assertTrue(DiagnosisCatalog.dsm5.size > 50)
        assertEquals(
            DiagnosisCatalog.icd10.size,
            DiagnosisCatalog.icd10.map { it.code }.distinct().size
        )
        assertEquals(
            DiagnosisCatalog.icd11.size,
            DiagnosisCatalog.icd11.map { it.code }.distinct().size
        )
        assertEquals(
            DiagnosisCatalog.dsm5.size,
            DiagnosisCatalog.dsm5.map { it.name }.distinct().size
        )
    }

    @Test
    fun search_matches_code_name_and_english_name() {
        val byCode = DiagnosisCatalog.search("ICD10", "F41.1")
        assertEquals(1, byCode.size)
        assertEquals("广泛性焦虑障碍", byCode.first().name)

        val byChinese = DiagnosisCatalog.search("ICD10", "广泛性焦虑")
        assertTrue(byChinese.any { it.code == "F41.1" })

        val byEnglish = DiagnosisCatalog.search("ICD10", "generalized anxiety")
        assertTrue(byEnglish.any { it.code == "F41.1" })

        val icd11 = DiagnosisCatalog.search("ICD11", "6A80.0")
        assertEquals(1, icd11.size)

        val dsm5 = DiagnosisCatalog.search("DSM5", "major depressive")
        assertTrue(dsm5.any { it.nameEn == "Major depressive disorder" })
    }

    @Test
    fun status_labels_support_both_languages() {
        assertEquals("Confirmed", DiagnosisCatalog.statusLabel("confirmed", true))
        assertEquals("已确诊", DiagnosisCatalog.statusLabel("confirmed", false))
        assertEquals("Suspected", DiagnosisCatalog.statusLabel("suspected", true))
        assertEquals("DSM-5", DiagnosisCatalog.standardLabel("DSM5", true))
        assertEquals("ICD-11", DiagnosisCatalog.standardLabel("ICD11", false))
    }

    @Test
    fun english_translation_handles_fixed_and_dynamic_text() {
        assertEquals("Today", translate("今天", AppLanguage.EN))
        assertEquals("MindTrace", translate("心迹", AppLanguage.EN))
        assertEquals("今天", translate("今天", AppLanguage.ZH))
        val dynamic = translate(
            "平均情绪 — / 5 · 平均睡眠 — 小时 · 药物 0 种",
            AppLanguage.EN
        )
        assertTrue(dynamic.contains("Average mood"))
        assertTrue(dynamic.contains("Average sleep"))
        assertFalse(dynamic.contains("平均"))
    }
}
