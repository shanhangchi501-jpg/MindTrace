package com.mindtrace.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一天一条记录。日期用 ISO 字符串（yyyy-MM-dd）保存，便于按天查询与跨年份排序。
 * 字段尽量可空：允许用户只记录此刻愿意记录的部分。
 */
@Serializable
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["date"], unique = true)]
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mood: Int? = null,                 // 1..5
    val anxiety: Int? = null,              // 0..10
    val irritability: Int? = null,         // 0..10
    val energy: Int? = null,               // 0..10
    val sleepHours: Float? = null,         // 0..24
    val sleepQuality: Int? = null,         // 0..10
    val appetite: Int? = null,             // 0..10
    val concentration: Int? = null,        // 0..10
    val symptoms: String = "",             // 逗号分隔的症状 id
    val symptomSeverity: String = "",      // 逗号分隔的 id:0-10，用于精神分裂症谱系等症状强度
    val triggers: String = "",             // 逗号分隔的诱因
    val suicidalIdeation: Int? = null,     // 0..5，见 SafetyLevels
    val medicationNote: String = "",
    val sideEffects: String = "",
    val notes: String = ""
)

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val birthYear: Int? = null,
    val gender: String = "",
    val conditions: String = "",           // 逗号分隔的病种标签
    val note: String = "",
    val updatedAt: Long = 0
)

@Serializable
@Entity(tableName = "symptoms")
data class SymptomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val conditionTags: String,
    val isCustom: Boolean = false,
    val enabled: Boolean = false,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

@Serializable
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val unit: String = "mg",
    val times: String = "08:00",           // 逗号分隔的 HH:mm
    val asNeeded: Boolean = false,
    val startDate: String,
    val endDate: String? = null,
    val active: Boolean = true,
    val notes: String = "",
    val createdAt: Long
)

fun MedicationEntity.displayLabel(): String =
    if (dosage.isBlank()) name.trim()
    else "${name.trim()} ${dosage.trim()}${unit.trim()}".trim()

@Serializable
@Entity(tableName = "medication_logs")
data class MedicationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val date: String,
    val time: String,
    val status: String,                    // taken / skipped / missed
    val takenAt: Long? = null,
    val note: String = ""
)

@Serializable
@Entity(tableName = "scale_assessments")
data class ScaleAssessmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                      // PHQ9 / GAD7
    val answers: String,                   // 逗号分隔的 0..3
    val score: Int,
    val severity: String,
    val createdAt: Long
)

@Serializable
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String = "",
    val content: String,
    val mood: Int? = null,
    val tags: String = ""
)

@Serializable
@Entity(tableName = "safety_plan")
data class SafetyPlanEntity(
    @PrimaryKey val id: Int = 1,
    val warningSigns: String = "",
    val copingStrategies: String = "",
    val reasonsToLive: String = "",
    val environmentSteps: String = "",
    val professionalNote: String = "",
    val updatedAt: Long = 0
)

@Serializable
@Entity(tableName = "crisis_contacts")
data class CrisisContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String = "",
    val phone: String,
    val kind: String = "trusted",          // trusted / professional / emergency
    val sortOrder: Int = 0
)

@Serializable
@Entity(tableName = "diagnoses")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val standard: String = "ICD10",        // ICD10 / ICD11 / CUSTOM
    val code: String = "",
    val name: String = "",
    val nameEn: String = "",
    val status: String = "confirmed",      // confirmed / suspected / remission / history
    val diagnosedDate: String? = null,
    val clinician: String = "",
    val note: String = "",
    val createdAt: Long = 0
)

fun DiagnosisEntity.displayName(english: Boolean): String =
    if (english && nameEn.isNotBlank()) nameEn else name

