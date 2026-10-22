package com.mindtrace.app

import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.data.SymptomCatalog
import com.mindtrace.app.data.displayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.temporal.ChronoUnit

class ScaleCatalogTest {

    @Test
    fun phq9_scores_and_severity_bands() {
        val phq9 = ScaleCatalog.phq9
        assertEquals(9, phq9.questions.size)
        assertEquals(27, phq9.maxScore)
        assertEquals(0, phq9.score(List(9) { 0 }))
        assertEquals(27, phq9.score(List(9) { 3 }))
        assertEquals("minimal", phq9.severity(0).key)
        assertEquals("minimal", phq9.severity(4).key)
        assertEquals("mild", phq9.severity(5).key)
        assertEquals("mild", phq9.severity(9).key)
        assertEquals("moderate", phq9.severity(10).key)
        assertEquals("moderate", phq9.severity(14).key)
        assertEquals("moderately_severe", phq9.severity(15).key)
        assertEquals("moderately_severe", phq9.severity(19).key)
        assertEquals("severe", phq9.severity(20).key)
        assertEquals("severe", phq9.severity(27).key)
    }

    @Test
    fun gad7_scores_and_severity_bands() {
        val gad7 = ScaleCatalog.gad7
        assertEquals(7, gad7.questions.size)
        assertEquals(21, gad7.maxScore)
        assertEquals(0, gad7.score(List(7) { 0 }))
        assertEquals(21, gad7.score(List(7) { 3 }))
        assertEquals("minimal", gad7.severity(4).key)
        assertEquals("mild", gad7.severity(5).key)
        assertEquals("moderate", gad7.severity(10).key)
        assertEquals("severe", gad7.severity(15).key)
        assertEquals("severe", gad7.severity(21).key)
    }

    @Test
    fun phq9_item_9_is_the_self_harm_question() {
        assertEquals(8, ScaleCatalog.phq9.selfHarmQuestionIndex)
        assertTrue(ScaleCatalog.phq9.questions[8].text.contains("伤害自己"))
    }

    @Test
    fun new_scales_have_expected_sizes_and_scores() {
        assertEquals(2, ScaleCatalog.phq2.questions.size)
        assertEquals(6, ScaleCatalog.phq2.maxScore)
        assertEquals(15, ScaleCatalog.phq15.questions.size)
        assertEquals(20, ScaleCatalog.pcl5.questions.size)
        assertEquals(80, ScaleCatalog.pcl5.maxScore)
        assertEquals(5, ScaleCatalog.who5.questions.size)
        assertEquals(25, ScaleCatalog.who5.maxScore)
        assertEquals(3, ScaleCatalog.auditc.questions.size)
        assertEquals(12, ScaleCatalog.auditc.maxScore)
        assertEquals(18, ScaleCatalog.itq.questions.size)
        assertEquals(21, ScaleCatalog.dass21.questions.size)
        assertEquals(19, ScaleCatalog.psqi.questions.size)
        assertEquals(21, ScaleCatalog.psqi.maxScore)
        assertEquals(24, ScaleCatalog.all.size)
    }

    @Test
    fun mdq_positive_screen_requires_all_three_criteria() {
        val answers = MutableList(15) { 1 }
        repeat(7) { answers[it] = 0 } // first seven are "Yes"
        answers[13] = 0 // same period: Yes
        answers[14] = 2 // moderate impairment
        assertEquals(7, ScaleCatalog.mdq.score(answers))
        assertEquals("positive", ScaleCatalog.mdq.severity(answers).key)

        answers[14] = 0
        assertEquals("partial", ScaleCatalog.mdq.severity(answers).key)

        answers[0] = 1
        assertEquals("negative", ScaleCatalog.mdq.severity(answers).key)
    }

    @Test
    fun dass21_detail_contains_three_subscales() {
        val answers = List(21) { 3 }
        val detail = ScaleCatalog.dass21.detail(answers, true)
        assertTrue(detail!!.contains("Depression"))
        assertTrue(detail.contains("Anxiety"))
        assertTrue(detail.contains("Stress"))
        assertTrue(detail.contains("extremely severe"))
    }

    @Test
    fun depression_suggestions_include_core_symptoms() {
        val suggested = SymptomCatalog.suggestedFor(listOf("depression"))
        assertTrue("low_mood" in suggested)
        assertTrue("anhedonia" in suggested)
        assertTrue("fatigue" in suggested)
        assertTrue("insomnia" in suggested)
    }

    @Test
    fun medication_label_omits_blank_dosage() {
        val noDose = MedicationEntity(
            name = "TestMed",
            dosage = "",
            unit = "mg",
            startDate = "2026-10-21",
            createdAt = 0
        )
        assertEquals("TestMed", noDose.displayLabel())

        val withDose = noDose.copy(dosage = "50")
        assertEquals("TestMed 50mg", withDose.displayLabel())
    }

    @Test
    fun date_range_is_inclusive() {
        val (start, end) = DateUtils.rangeForDays(7)
        val startDate = DateUtils.parse(start)!!
        val endDate = DateUtils.parse(end)!!
        assertEquals(6, ChronoUnit.DAYS.between(startDate, endDate))
    }
}
