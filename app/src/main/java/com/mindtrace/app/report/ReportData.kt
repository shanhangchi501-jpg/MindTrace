package com.mindtrace.app.report

import com.mindtrace.app.data.AdherenceSummary
import com.mindtrace.app.data.AppContainer
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DiagnosisEntity
import com.mindtrace.app.data.JournalEntryEntity
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.data.MedicationLogEntity
import com.mindtrace.app.data.ProfileEntity
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.SafetyCatalog
import com.mindtrace.app.data.parseSymptomSeverity
import java.time.LocalDate

data class SymptomStat(
    val id: String,
    val name: String,
    val days: Int,
    val averageSeverity: Float?,
    val maxSeverity: Int?
)

enum class ReportLayout {
    CLINICAL,
    COMPACT
}

data class ReportOptions(
    val includeJournals: Boolean = false,
    val includeNotes: Boolean = true,
    val doctorQuestions: String = "",
    val layout: ReportLayout = ReportLayout.CLINICAL
)

data class ReportData(
    val startDate: String,
    val endDate: String,
    val generatedAt: Long,
    val profile: ProfileEntity?,
    val checkIns: List<CheckInEntity>,
    val medications: List<MedicationEntity>,
    val medicationLogs: List<MedicationLogEntity>,
    val scales: List<ScaleAssessmentEntity>,
    val journals: List<JournalEntryEntity>,
    val symptomNames: Map<String, String>,
    val adherence: AdherenceSummary,
    val options: ReportOptions,
    val diagnoses: List<DiagnosisEntity> = emptyList()
) {
    val recordedDays: Int
        get() = checkIns.count {
            it.mood != null || it.anxiety != null || it.sleepHours != null ||
                it.symptoms.isNotBlank() || it.notes.isNotBlank()
        }

    val averageMood: Float?
        get() = checkIns.mapNotNull { it.mood?.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val averageAnxiety: Float?
        get() = checkIns.mapNotNull { it.anxiety?.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val averageSleep: Float?
        get() = checkIns.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val symptomFrequency: List<Pair<String, Int>>
        get() = symptomStats.map { it.name to it.days }

    /** 症状出现天数与已记录的 0–10 强度均值。 */
    val symptomStats: List<SymptomStat>
        get() {
            data class Acc(val levels: MutableList<Int> = mutableListOf(), var days: Int = 0)
            val stats = linkedMapOf<String, Acc>()
            checkIns.forEach { entry ->
                val severity = parseSymptomSeverity(entry.symptomSeverity)
                entry.symptoms.split(',').filter { it.isNotBlank() }.distinct().forEach { id ->
                    val acc = stats.getOrPut(id) { Acc() }
                    acc.days++
                    severity[id]?.let { acc.levels.add(it) }
                }
            }
            return stats.entries
                .map { (id, acc) ->
                    SymptomStat(
                        id = id,
                        name = symptomNames[id] ?: id,
                        days = acc.days,
                        averageSeverity = acc.levels.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                        maxSeverity = acc.levels.maxOrNull()
                    )
                }
                .sortedWith(
                    compareByDescending<SymptomStat> { it.days }
                        .thenByDescending { it.averageSeverity ?: -1f }
                )
        }

    val ideationEvents: List<Pair<String, String>>
        get() = checkIns.mapNotNull { entry ->
            val level = entry.suicidalIdeation ?: return@mapNotNull null
            if (level <= 0) return@mapNotNull null
            val label = SafetyCatalog.levels.firstOrNull { it.value == level }?.label ?: level.toString()
            entry.date to label
        }

    val latestScales: List<ScaleAssessmentEntity>
        get() = scales.groupBy { it.type }.mapNotNull { (_, list) -> list.maxByOrNull { it.createdAt } }
}

object ReportBuilder {
    suspend fun build(
        container: AppContainer,
        startDate: String,
        endDate: String,
        options: ReportOptions
    ): ReportData {
        val allCheckIns = container.checkInRepository.getAll()
            .filter { it.date in startDate..endDate }
            .sortedBy { it.date }
        val allJournals = if (options.includeJournals) {
            container.journalRepository.getAll()
                .filter { it.date in startDate..endDate }
                .sortedBy { it.date }
        } else {
            emptyList()
        }
        val allMeds = container.medicationRepository.getAll()
            .filter { med ->
                med.startDate <= endDate && (med.endDate == null || med.endDate >= startDate)
            }
        val logs = container.medicationRepository.getAllLogs()
            .filter { it.date in startDate..endDate }
        val scales = container.scaleRepository.getAll()
            .filter {
                val date = DateUtils.toLocalDateTime(it.createdAt).toLocalDate()
                DateUtils.iso(date) in startDate..endDate
            }
            .sortedBy { it.createdAt }
        val symptoms = container.symptomRepository.getAll()
        val adherence = container.medicationRepository.adherence(startDate, endDate)

        return ReportData(
            startDate = startDate,
            endDate = endDate,
            generatedAt = System.currentTimeMillis(),
            profile = container.profileRepository.get(),
            checkIns = allCheckIns,
            medications = allMeds,
            medicationLogs = logs,
            scales = scales,
            journals = allJournals,
            symptomNames = symptoms.associate { it.id to it.name },
            adherence = adherence,
            options = options,
            diagnoses = container.diagnosisRepository.getAll()
        )
    }

    fun dateRangeForDays(days: Int): Pair<String, String> = DateUtils.rangeForDays(days)

    fun today(): LocalDate = DateUtils.today()
}
