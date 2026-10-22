package com.mindtrace.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY date DESC")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeRange(start: String, end: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_ins ORDER BY date ASC")
    suspend fun getAll(): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInEntity): Long

    @Delete
    suspend fun delete(entity: CheckInEntity)

    @Query("DELETE FROM check_ins")
    suspend fun clear()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1 LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptoms WHERE archived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms WHERE archived = 0 AND enabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeEnabled(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms")
    suspend fun getAll(): List<SymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SymptomEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SymptomEntity)

    @Query("UPDATE symptoms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM symptoms WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)

    @Query("SELECT COUNT(*) FROM symptoms")
    suspend fun count(): Int

    @Query("DELETE FROM symptoms")
    suspend fun clear()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY active DESC, createdAt ASC")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getActive(): List<MedicationEntity>

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationEntity): Long

    @Delete
    suspend fun delete(entity: MedicationEntity)

    @Query("UPDATE medications SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("DELETE FROM medications")
    suspend fun clear()
}

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC")
    fun observeRange(start: String, end: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs WHERE date = :date")
    fun observeForDate(date: String): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId AND date = :date AND time = :time LIMIT 1")
    suspend fun getFor(medicationId: Long, date: String, time: String): MedicationLogEntity?

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId AND date = :date")
    suspend fun getForDate(medicationId: Long, date: String): List<MedicationLogEntity>

    @Query("SELECT * FROM medication_logs ORDER BY date ASC, time ASC")
    suspend fun getAll(): List<MedicationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationLogEntity): Long

    @Query("DELETE FROM medication_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Query("DELETE FROM medication_logs")
    suspend fun clear()
}

@Dao
interface ScaleDao {
    @Query("SELECT * FROM scale_assessments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScaleAssessmentEntity>>

    @Query("SELECT * FROM scale_assessments WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<ScaleAssessmentEntity>>

    @Query("SELECT * FROM scale_assessments ORDER BY createdAt ASC")
    suspend fun getAll(): List<ScaleAssessmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScaleAssessmentEntity): Long

    @Query("DELETE FROM scale_assessments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scale_assessments")
    suspend fun clear()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries ORDER BY date ASC")
    suspend fun getAll(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JournalEntryEntity): Long

    @Delete
    suspend fun delete(entity: JournalEntryEntity)

    @Query("DELETE FROM journal_entries")
    suspend fun clear()
}

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnoses ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnoses ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<DiagnosisEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DiagnosisEntity): Long

    @Delete
    suspend fun delete(entity: DiagnosisEntity)

    @Query("DELETE FROM diagnoses")
    suspend fun clear()
}

@Dao
interface SafetyDao {
    @Query("SELECT * FROM safety_plan WHERE id = 1 LIMIT 1")
    fun observePlan(): Flow<SafetyPlanEntity?>

    @Query("SELECT * FROM safety_plan WHERE id = 1 LIMIT 1")
    suspend fun getPlan(): SafetyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(entity: SafetyPlanEntity)

    @Query("SELECT * FROM crisis_contacts ORDER BY sortOrder ASC, id ASC")
    fun observeContacts(): Flow<List<CrisisContactEntity>>

    @Query("SELECT * FROM crisis_contacts ORDER BY sortOrder ASC, id ASC")
    suspend fun getContacts(): List<CrisisContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(entity: CrisisContactEntity): Long

    @Delete
    suspend fun deleteContact(entity: CrisisContactEntity)

    @Query("DELETE FROM crisis_contacts")
    suspend fun clearContacts()

    @Query("DELETE FROM safety_plan")
    suspend fun clearPlan()
}
