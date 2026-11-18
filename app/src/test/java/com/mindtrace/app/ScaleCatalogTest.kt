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
        assertEquals(18, ScaleCatalog.psqi.questions.size)
        assertEquals(21, ScaleCatalog.psqi.maxScore)
        assertEquals(48, ScaleCatalog.all.size)
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

    @Test
    fun asrs18_sums_parts_and_uses_official_thresholds() {
        val asrs = ScaleCatalog.asrs18
        assertEquals(18, asrs.questions.size)
        assertEquals(36, asrs.maxScore)
        assertEquals(9, asrs.score(List(18) { 1 }))
        assertEquals("unlikely", asrs.severity(List(18) { 1 }).key)

        val partALikely = MutableList(18) { 0 }
        for (i in 0..8) partALikely[i] = 2
        assertEquals(18, asrs.score(partALikely))
        assertEquals("adhd_i", asrs.severity(partALikely).key)

        val partBVeryLikely = MutableList(18) { 0 }
        for (i in 9..17) partBVeryLikely[i] = 3
        assertEquals(27, asrs.score(partBVeryLikely))
        assertEquals("adhd_h", asrs.severity(partBVeryLikely).key)

        val bothLikely = MutableList(18) { 2 }
        assertEquals("adhd_c", asrs.severity(bothLikely).key)

        val detail = asrs.detail(partALikely, true)!!
        assertTrue(detail.contains("Part A (inattention) 18 / 36"))
        assertTrue(detail.contains("Part B (hyperactivity-impulsivity) 0 / 36"))
        // 旧版计数记录仍可显示名称
        assertTrue(asrs.band("adhd_i") != null)
    }

    @Test
    fun bsl23_uses_mean_and_excludes_supplement_items() {
        val bsl = ScaleCatalog.bsl23
        assertEquals(34, bsl.questions.size)
        assertEquals(23, bsl.scoredQuestionCount)
        assertEquals(40, bsl.maxScore)
        assertEquals("0.0", bsl.formatScore(0))
        assertEquals("1.5", bsl.formatScore(15))
        assertEquals("4.0", bsl.formatMaxScore())
        assertEquals(0, bsl.score(List(34) { 0 }))
        assertEquals(40, bsl.score(List(34) { 4 }))
        assertEquals(10, bsl.score(List(23) { 1 } + List(11) { 0 }))
        assertEquals("moderate", bsl.severity(15).key)
        // 补充题不参与总分
        val withSupplement = MutableList(34) { 0 }
        withSupplement[23] = 4
        assertEquals(0, bsl.score(withSupplement))
    }

    @Test
    fun psqi_full_seven_component_scoring() {
        val psqi = ScaleCatalog.psqi
        assertEquals(18, psqi.questions.size)
        assertEquals(0, psqi.score(List(18) { 0 }))
        val answers = MutableList(18) { 0 }
        answers[0] = 4 // 23:00–00:00
        answers[2] = 4 // 07:00–08:00, 8 hours in bed
        answers[3] = 3 // 4.5 hours sleep -> duration component 3 and efficiency component 3
        assertEquals(6, psqi.score(answers))
    }

    @Test
    fun ders36_reverse_scoring_and_subscales() {
        val ders = ScaleCatalog.ders36
        assertEquals(36, ders.questions.size)
        assertEquals(180, ders.maxScore)
        assertEquals(80, ders.score(List(36) { 0 }))
        assertEquals(136, ders.score(List(36) { 4 }))
        assertTrue(ders.detail(List(36) { 2 }, true)!!.contains("Strategies"))
    }

    @Test
    fun shaps_hedonic_tone_scoring() {
        val shaps = ScaleCatalog.shaps
        assertEquals(14, shaps.questions.size)
        assertEquals(14, shaps.maxScore)
        assertEquals(0, shaps.score(List(14) { 0 }))
        assertEquals(14, shaps.score(List(14) { 3 }))
        assertEquals("normal", shaps.severity(2).key)
        assertEquals("mild", shaps.severity(3).key)
    }

    @Test
    fun iesr_and_spin_totals() {
        assertEquals(22, ScaleCatalog.iesr.questions.size)
        assertEquals(88, ScaleCatalog.iesr.maxScore)
        assertEquals(88, ScaleCatalog.iesr.score(List(22) { 4 }))
        assertEquals(17, ScaleCatalog.spin.questions.size)
        assertEquals(68, ScaleCatalog.spin.maxScore)
        assertEquals(68, ScaleCatalog.spin.score(List(17) { 4 }))
    }

    @Test
    fun cssrs_triage_uses_high_risk_items() {
        val cssrs = ScaleCatalog.cssrs
        assertEquals(7, cssrs.questions.size)
        assertEquals("negative", cssrs.severity(List(7) { 0 }).key)
        val ideation = MutableList(7) { 0 }
        ideation[1] = 1 // item 2 yes
        assertEquals("moderate", cssrs.severity(ideation).key)
        val intent = MutableList(7) { 0 }
        intent[3] = 1 // item 4 yes
        assertEquals("high", cssrs.severity(intent).key)
        val lifetimeBehaviour = MutableList(7) { 0 }
        lifetimeBehaviour[5] = 1 // item 6 yes
        assertEquals("moderate", cssrs.severity(lifetimeBehaviour).key)
        val recentBehaviour = lifetimeBehaviour.toMutableList()
        recentBehaviour[6] = 2 // item 7 yes
        assertEquals("high", cssrs.severity(recentBehaviour).key)
    }

    @Test
    fun pq16_ess_mspss_and_ffmq_scores() {
        assertEquals(16, ScaleCatalog.pq16.questions.size)
        assertEquals(16, ScaleCatalog.pq16.score(List(16) { 1 }))
        assertEquals("above", ScaleCatalog.pq16.severity(6).key)

        assertEquals(8, ScaleCatalog.ess.questions.size)
        assertEquals(24, ScaleCatalog.ess.score(List(8) { 3 }))
        assertEquals("higher_normal", ScaleCatalog.ess.severity(6).key)
        assertEquals("mild", ScaleCatalog.ess.severity(11).key)

        assertEquals(12, ScaleCatalog.mspss.questions.size)
        assertEquals(12, ScaleCatalog.mspss.score(List(12) { 0 }))
        assertEquals(84, ScaleCatalog.mspss.score(List(12) { 6 }))
        assertEquals("low", ScaleCatalog.mspss.severity(12).key)

        val ffmq = ScaleCatalog.ffmq39
        assertEquals(39, ffmq.questions.size)
        assertEquals(195, ffmq.maxScore)
        assertEquals(115, ffmq.score(List(39) { 0 }))
        assertTrue(ffmq.detail(List(39) { 2 }, true)!!.contains("Observing"))
    }
}
