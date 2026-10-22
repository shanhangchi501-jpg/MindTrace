package com.mindtrace.app.data

import androidx.room.withTransaction
import com.mindtrace.app.ui.i18n.translate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val profile: ProfileEntity? = null,
    val checkIns: List<CheckInEntity> = emptyList(),
    val symptoms: List<SymptomEntity> = emptyList(),
    val medications: List<MedicationEntity> = emptyList(),
    val medicationLogs: List<MedicationLogEntity> = emptyList(),
    val scales: List<ScaleAssessmentEntity> = emptyList(),
    val journals: List<JournalEntryEntity> = emptyList(),
    val safetyPlan: SafetyPlanEntity? = null,
    val contacts: List<CrisisContactEntity> = emptyList(),
    val diagnoses: List<DiagnosisEntity> = emptyList()
)

object BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val MAGIC = byteArrayOf(0x4D, 0x54, 0x52, 0x01) // MTR + version
    private const val ITERATIONS = 200_000
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

    suspend fun collect(container: AppContainer): BackupPayload = BackupPayload(
        exportedAt = System.currentTimeMillis(),
        profile = container.profileRepository.get(),
        checkIns = container.checkInRepository.getAll(),
        symptoms = container.symptomRepository.getAll(),
        medications = container.medicationRepository.getAll(),
        medicationLogs = container.medicationRepository.getAllLogs(),
        scales = container.scaleRepository.getAll(),
        journals = container.journalRepository.getAll(),
        safetyPlan = container.safetyRepository.getPlan(),
        contacts = container.safetyRepository.getContacts(),
        diagnoses = container.diagnosisRepository.getAll()
    )

    suspend fun writeEncrypted(
        container: AppContainer,
        output: OutputStream,
        password: CharArray
    ) {
        val payload = collect(container)
        val text = json.encodeToString(BackupPayload.serializer(), payload)
        encrypt(text.toByteArray(Charsets.UTF_8), password) { salt, iv, cipherText ->
            DataOutputStream(output).use { out ->
                out.write(MAGIC)
                out.writeInt(salt.size)
                out.write(salt)
                out.writeInt(iv.size)
                out.write(iv)
                out.writeInt(cipherText.size)
                out.write(cipherText)
                out.flush()
            }
        }
    }

    suspend fun restoreEncrypted(
        container: AppContainer,
        input: InputStream,
        password: CharArray
    ): Result<Unit> = runCatching {
        val bytes = decrypt(input, password)
        val payload = json.decodeFromString(BackupPayload.serializer(), bytes.toString(Charsets.UTF_8))
        require(payload.version in 1..1) { "备份版本不受支持" }
        restore(container, payload)
    }

    private suspend fun restore(container: AppContainer, payload: BackupPayload) {
        val db = container.database
        db.withTransaction {
            db.profileDao().clear()
            db.checkInDao().clear()
            db.symptomDao().clear()
            db.medicationLogDao().clear()
            db.medicationDao().clear()
            db.scaleDao().clear()
            db.journalDao().clear()
            db.safetyDao().clearContacts()
            db.safetyDao().clearPlan()
            db.diagnosisDao().clear()

            db.profileDao().upsert(payload.profile ?: ProfileEntity())
            payload.checkIns.forEach { db.checkInDao().upsert(it) }
            if (payload.symptoms.isEmpty()) {
                db.symptomDao().upsertAll(
                    SymptomCatalog.seedEntities(SymptomCatalog.suggestedFor(listOf("unsure")))
                )
            } else {
                db.symptomDao().upsertAll(payload.symptoms)
            }
            payload.medications.forEach { db.medicationDao().upsert(it) }
            payload.medicationLogs.forEach { db.medicationLogDao().upsert(it) }
            payload.scales.forEach { db.scaleDao().insert(it) }
            payload.journals.forEach { db.journalDao().upsert(it) }
            payload.safetyPlan?.let { db.safetyDao().upsertPlan(it) }
            payload.contacts.forEach { db.safetyDao().upsertContact(it) }
            payload.diagnoses.forEach { db.diagnosisDao().upsert(it) }
        }
    }

    suspend fun writeCsv(container: AppContainer, output: OutputStream, language: AppLanguage) {
        val entries = container.checkInRepository.getAll()
        val symptomNames = container.symptomRepository.getAll().associate { it.id to it.name }
        val english = language.isEnglish
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write('﻿'.code)
            if (english) {
                writer.write(
                    "Date,Mood (1-5),Anxiety (0-10),Irritability (0-10),Energy (0-10)," +
                        "Sleep hours,Sleep quality (0-10),Appetite (0-10),Concentration (0-10)," +
                        "Symptoms,Triggers,Self-harm thoughts,Medication,Side effects,Notes\r\n"
                )
            } else {
                writer.write(
                    "日期,情绪(1-5),焦虑(0-10),易怒(0-10),精力(0-10),睡眠小时,睡眠质量(0-10)," +
                        "食欲(0-10),注意力(0-10),症状,诱因,伤害自己的念头,服药情况,副作用,备注\r\n"
                )
            }
            entries.forEach { e ->
                val severity = parseSymptomSeverity(e.symptomSeverity)
                val symptoms = e.symptoms.split(',').filter { it.isNotBlank() }
                    .joinToString(if (english) "; " else "、") { id ->
                        val name = translate(symptomNames[id] ?: id, language)
                        severity[id]?.let { name + " ($it/10)" } ?: name
                    }
                val ideation = e.suicidalIdeation?.let { value ->
                    val label = SafetyCatalog.levels.firstOrNull { it.value == value }?.label
                        ?: value.toString()
                    translate(label, language)
                } ?: ""
                val triggers = e.triggers.split(',').filter { it.isNotBlank() }
                    .joinToString(if (english) "; " else "、") { translate(it, language) }
                val row = listOf(
                    e.date,
                    e.mood?.toString() ?: "",
                    e.anxiety?.toString() ?: "",
                    e.irritability?.toString() ?: "",
                    e.energy?.toString() ?: "",
                    e.sleepHours?.toString() ?: "",
                    e.sleepQuality?.toString() ?: "",
                    e.appetite?.toString() ?: "",
                    e.concentration?.toString() ?: "",
                    symptoms,
                    triggers,
                    ideation,
                    e.medicationNote,
                    e.sideEffects,
                    e.notes
                )
                writer.write(row.joinToString(",") { csvCell(it) })
                writer.write("\r\n")
            }
        }
    }

    private fun csvCell(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuote = value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private inline fun encrypt(
        plain: ByteArray,
        password: CharArray,
        write: (ByteArray, ByteArray, ByteArray) -> Unit
    ) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plain)
        write(salt, iv, cipherText)
    }

    private fun decrypt(input: InputStream, password: CharArray): ByteArray {
        DataInputStream(input).use { stream ->
            val magic = ByteArray(MAGIC.size)
            stream.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "不是有效的心迹备份文件" }
            val saltLength = stream.readInt()
            require(saltLength in 8..64) { "备份文件损坏" }
            val salt = ByteArray(saltLength).also { stream.readFully(it) }
            val ivLength = stream.readInt()
            require(ivLength in 8..32) { "备份文件损坏" }
            val iv = ByteArray(ivLength).also { stream.readFully(it) }
            val cipherLength = stream.readInt()
            require(cipherLength in 1..MAX_BACKUP_BYTES) { "备份文件过大或已损坏" }
            val cipherText = ByteArray(cipherLength).also { stream.readFully(it) }

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return cipher.doFinal(cipherText)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
