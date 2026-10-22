package com.mindtrace.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ProfileRepository(private val dao: ProfileDao) {
    fun observe(): Flow<ProfileEntity?> = dao.observe()
    suspend fun get(): ProfileEntity? = dao.get()
    suspend fun getOrCreate(): ProfileEntity = dao.get() ?: ProfileEntity().also { dao.upsert(it) }
    suspend fun save(profile: ProfileEntity) = dao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))

    suspend fun update(
        displayName: String? = null,
        birthYear: Int? = null,
        gender: String? = null,
        conditions: List<String>? = null,
        note: String? = null
    ) {
        val current = getOrCreate()
        dao.upsert(
            current.copy(
                displayName = displayName ?: current.displayName,
                birthYear = if (birthYear != null) birthYear else current.birthYear,
                gender = gender ?: current.gender,
                conditions = conditions?.joinToString(",") ?: current.conditions,
                note = note ?: current.note,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

class SymptomRepository(private val dao: SymptomDao) {
    fun observeAll(): Flow<List<SymptomEntity>> = dao.observeAll()
    fun observeEnabled(): Flow<List<SymptomEntity>> = dao.observeEnabled()
    suspend fun getAll(): List<SymptomEntity> = dao.getAll()
    suspend fun ensureSeeded(defaultConditions: List<String> = listOf("unsure")) {
        val existing = dao.getAll().associateBy { it.id }
        val suggested = SymptomCatalog.suggestedFor(defaultConditions)
        // 每次都合并内置目录：新增症状会出现在设置里，旧用户手动开关
        // 的状态保持不变；新用户按所选病种获得建议勾选。
        val merged = SymptomCatalog.symptoms.mapIndexed { index, def ->
            val old = existing[def.id]
            SymptomEntity(
                id = def.id,
                name = def.name,
                category = def.category,
                conditionTags = def.conditionTags.joinToString(","),
                isCustom = false,
                enabled = old?.enabled ?: (def.id in suggested),
                sortOrder = index,
                archived = old?.archived ?: false
            )
        }
        dao.upsertAll(merged)
    }

    suspend fun applySuggestedConditions(conditionKeys: Collection<String>) {
        val enabled = SymptomCatalog.suggestedFor(conditionKeys)
        val existing = dao.getAll().associateBy { it.id }
        // 只对内置症状应用建议；保留用户手动开启但不在建议里的症状。
        val updated = existing.values.map { entity ->
            if (!entity.isCustom && entity.id in enabled) entity.copy(enabled = true) else entity
        }
        val missing = SymptomCatalog.symptoms
            .filter { it.id !in existing && it.id in enabled }
            .mapIndexed { index, def ->
                SymptomEntity(
                    id = def.id,
                    name = def.name,
                    category = def.category,
                    conditionTags = def.conditionTags.joinToString(","),
                    enabled = true,
                    sortOrder = existing.size + index
                )
            }
        dao.upsertAll(updated + missing)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun setEnabledSymptoms(ids: Set<String>) {
        dao.getAll().forEach { dao.setEnabled(it.id, it.id in ids) }
    }
    suspend fun addCustom(name: String): String {
        val id = "custom_" + System.currentTimeMillis()
        val all = dao.getAll()
        dao.upsert(
            SymptomEntity(
                id = id,
                name = name.trim(),
                category = "自定义",
                conditionTags = "",
                isCustom = true,
                enabled = true,
                sortOrder = all.size
            )
        )
        return id
    }

    suspend fun deleteCustom(id: String) = dao.deleteCustom(id)
}

class CheckInRepository(private val dao: CheckInDao) {
    fun observeAll(): Flow<List<CheckInEntity>> = dao.observeAll()
    fun observeRange(start: String, end: String): Flow<List<CheckInEntity>> = dao.observeRange(start, end)
    fun observeByDate(date: String): Flow<CheckInEntity?> = dao.observeByDate(date)
    suspend fun getByDate(date: String): CheckInEntity? = dao.getByDate(date)
    suspend fun getAll(): List<CheckInEntity> = dao.getAll()

    suspend fun save(entity: CheckInEntity): Long {
        val existing = dao.getByDate(entity.date)
        val now = System.currentTimeMillis()
        return dao.upsert(
            entity.copy(
                id = existing?.id ?: entity.id,
                createdAt = existing?.createdAt ?: entity.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun delete(entity: CheckInEntity) = dao.delete(entity)
    suspend fun clear() = dao.clear()

    companion object {
        fun symptomIds(entity: CheckInEntity?): Set<String> =
            entity?.symptoms?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        fun triggerNames(entity: CheckInEntity?): Set<String> =
            entity?.triggers?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
}

enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED, AS_NEEDED }

data class DoseSlot(
    val medication: MedicationEntity,
    val time: String,
    val status: DoseStatus,
    val log: MedicationLogEntity? = null
)

/** 解析 "symptomId:0-10" 形式的症状强度。 */
fun parseSymptomSeverity(raw: String): Map<String, Int> =
    raw.split(',').mapNotNull { part ->
        val index = part.lastIndexOf(':')
        if (index <= 0) return@mapNotNull null
        val id = part.substring(0, index).trim()
        val value = part.substring(index + 1).trim().toIntOrNull()
        if (id.isBlank() || value == null || value !in 0..10) null else id to value
    }.toMap()

fun encodeSymptomSeverity(values: Map<String, Int>): String =
    values.entries
        .filter { it.value in 0..10 }
        .sortedBy { it.key }
        .joinToString(",") { "${it.key}:${it.value}" }

data class AdherenceSummary(
    val total: Int = 0,
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((taken * 100f) / total).toInt().coerceIn(0, 100)
}

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val logDao: MedicationLogDao
) {
    fun observeActive(): Flow<List<MedicationEntity>> = medicationDao.observeActive()
    fun observeAll(): Flow<List<MedicationEntity>> = medicationDao.observeAll()
    fun observeLogs(start: String, end: String): Flow<List<MedicationLogEntity>> =
        logDao.observeRange(start, end)

    fun observeLogsForDate(date: String): Flow<List<MedicationLogEntity>> = logDao.observeForDate(date)

    suspend fun getActive(): List<MedicationEntity> = medicationDao.getActive()
    suspend fun getById(id: Long): MedicationEntity? = medicationDao.getById(id)
    suspend fun getAllLogs(): List<MedicationLogEntity> = logDao.getAll()
    suspend fun getAll(): List<MedicationEntity> = medicationDao.getAll()

    suspend fun save(medication: MedicationEntity): Long = medicationDao.upsert(medication)
    suspend fun delete(medication: MedicationEntity) {
        logDao.deleteForMedication(medication.id)
        medicationDao.delete(medication)
    }
    suspend fun setActive(id: Long, active: Boolean) = medicationDao.setActive(id, active)

    suspend fun log(medicationId: Long, date: String, time: String, status: DoseStatus, note: String = "") {
        if (status == DoseStatus.AS_NEEDED) {
            logDao.upsert(
                MedicationLogEntity(
                    medicationId = medicationId,
                    date = date,
                    time = "",
                    status = "taken",
                    takenAt = System.currentTimeMillis(),
                    note = note
                )
            )
            return
        }
        val existing = logDao.getFor(medicationId, date, time)
        logDao.upsert(
            MedicationLogEntity(
                id = existing?.id ?: 0,
                medicationId = medicationId,
                date = date,
                time = time,
                status = when (status) {
                    DoseStatus.TAKEN -> "taken"
                    DoseStatus.SKIPPED -> "skipped"
                    else -> "missed"
                },
                takenAt = if (status == DoseStatus.TAKEN) System.currentTimeMillis() else null,
                note = note
            )
        )
    }

    suspend fun clearLog(medicationId: Long, date: String, time: String) {
        val existing = logDao.getFor(medicationId, date, time) ?: return
        logDao.deleteById(existing.id)
    }

    suspend fun deleteLog(id: Long) = logDao.deleteById(id)

    suspend fun dosesFor(date: String, medications: List<MedicationEntity>, logs: List<MedicationLogEntity>): List<DoseSlot> {
        val target = DateUtils.parse(date) ?: return emptyList()
        val today = DateUtils.today()
        val logKey = logs.associateBy { it.medicationId to it.time }
        return medications
            .filter { it.active && it.startDate <= date && (it.endDate == null || it.endDate >= date) }
            .filter { !it.asNeeded }
            .flatMap { med ->
                med.times.split(',').filter { it.isNotBlank() }.map { time ->
                    val log = logKey[med.id to time]
                    val status = when {
                        log?.status == "taken" -> DoseStatus.TAKEN
                        log?.status == "skipped" -> DoseStatus.SKIPPED
                        log?.status == "missed" -> DoseStatus.MISSED
                        target.isBefore(today) -> DoseStatus.MISSED
                        else -> DoseStatus.PENDING
                    }
                    DoseSlot(med, time, status, log)
                }
            }
            .sortedBy { it.time }
    }

    suspend fun adherence(start: String, end: String): AdherenceSummary {
        val meds = medicationDao.getActive()
        val logs = logDao.getAll()
        val startDate = DateUtils.parse(start) ?: return AdherenceSummary()
        val endDate = DateUtils.parse(end) ?: return AdherenceSummary()
        val today = DateUtils.today()
        val logKey = logs.associateBy { Triple(it.medicationId, it.date, it.time) }
        var total = 0
        var taken = 0
        var missed = 0
        var day = startDate
        while (!day.isAfter(endDate)) {
            val date = DateUtils.iso(day)
            val isToday = day == today
            if (day.isBefore(today) || isToday) {
                meds.filter { it.startDate <= date && (it.endDate == null || it.endDate >= date) }
                    .filter { !it.asNeeded }
                    .forEach { med ->
                        med.times.split(',').filter { it.isNotBlank() }.forEach { time ->
                            val log = logKey[Triple(med.id, date, time)]
                            // 今天的未打卡剂量不计入分母，但今天已记录的剂量会计入，
                            // 这样完成今天的用药后能立刻看到依从率变化。
                            if (day.isBefore(today) || log != null) {
                                // 「跳过」已不再是可用操作；历史 skipped 记录不计入
                                // 分母，避免旧数据继续拉低依从率。
                                when (log?.status) {
                                    "taken" -> {
                                        total++
                                        taken++
                                    }
                                    "skipped" -> Unit
                                    "missed" -> {
                                        total++
                                        missed++
                                    }
                                    else -> {
                                        total++
                                        missed++
                                    }
                                }
                            }
                        }
                    }
            }
            day = day.plusDays(1)
        }
        return AdherenceSummary(total, taken, 0, missed)
    }

    suspend fun clear() {
        logDao.clear()
        medicationDao.clear()
    }
}

class ScaleRepository(private val dao: ScaleDao) {
    fun observeAll(): Flow<List<ScaleAssessmentEntity>> = dao.observeAll()
    fun observeByType(type: String): Flow<List<ScaleAssessmentEntity>> = dao.observeByType(type)
    suspend fun getAll(): List<ScaleAssessmentEntity> = dao.getAll()

    suspend fun save(type: String, answers: List<Int>): ScaleAssessmentEntity {
        val def = requireNotNull(ScaleCatalog.byType(type)) { "Unknown scale $type" }
        val score = def.score(answers)
        val band = def.severity(answers)
        val entity = ScaleAssessmentEntity(
            type = type,
            answers = answers.joinToString(","),
            score = score,
            severity = band.key,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(entity)
        return entity
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun clear() = dao.clear()
}

class JournalRepository(private val dao: JournalDao) {
    fun observeAll(): Flow<List<JournalEntryEntity>> = dao.observeAll()
    fun observeByDate(date: String): Flow<List<JournalEntryEntity>> =
        dao.observeAll().map { list -> list.filter { it.date == date } }

    suspend fun get(id: Long): JournalEntryEntity? = dao.getById(id)
    suspend fun getAll(): List<JournalEntryEntity> = dao.getAll()
    suspend fun save(entry: JournalEntryEntity): Long {
        val now = System.currentTimeMillis()
        return dao.upsert(
            entry.copy(
                createdAt = entry.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now
            )
        )
    }
    suspend fun delete(entry: JournalEntryEntity) = dao.delete(entry)
    suspend fun clear() = dao.clear()
}

class DiagnosisRepository(private val dao: DiagnosisDao) {
    fun observeAll(): Flow<List<DiagnosisEntity>> = dao.observeAll()
    suspend fun getAll(): List<DiagnosisEntity> = dao.getAll()
    suspend fun save(entity: DiagnosisEntity): Long = dao.upsert(
        entity.copy(
            createdAt = entity.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    )
    suspend fun delete(entity: DiagnosisEntity) = dao.delete(entity)
    suspend fun clear() = dao.clear()
}

class SafetyRepository(private val dao: SafetyDao) {
    fun observePlan(): Flow<SafetyPlanEntity?> = dao.observePlan()
    fun observeContacts(): Flow<List<CrisisContactEntity>> = dao.observeContacts()
    suspend fun getPlan(): SafetyPlanEntity? = dao.getPlan()
    suspend fun getContacts(): List<CrisisContactEntity> = dao.getContacts()
    suspend fun savePlan(plan: SafetyPlanEntity) =
        dao.upsertPlan(plan.copy(id = 1, updatedAt = System.currentTimeMillis()))
    suspend fun saveContact(contact: CrisisContactEntity): Long = dao.upsertContact(contact)
    suspend fun deleteContact(contact: CrisisContactEntity) = dao.deleteContact(contact)
    suspend fun clear() {
        dao.clearContacts()
        dao.clearPlan()
    }
}

fun List<CheckInEntity>.averageMood(): Float? =
    mapNotNull { it.mood?.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

fun List<CheckInEntity>.averageAnxiety(): Float? =
    mapNotNull { it.anxiety?.toFloat() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

fun List<CheckInEntity>.averageSleep(): Float? =
    mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

fun List<CheckInEntity>.symptomFrequency(): List<Pair<String, Int>> {
    val counts = mutableMapOf<String, Int>()
    forEach { entry ->
        entry.symptoms.split(',').filter { it.isNotBlank() }.forEach {
            counts[it] = (counts[it] ?: 0) + 1
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

fun LocalDate.iso(): String = DateUtils.iso(this)
