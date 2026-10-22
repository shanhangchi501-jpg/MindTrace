package com.mindtrace.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = SettingsStore(appContext)
    val keyManager = DatabaseKeyManager(appContext)

    init {
        AppLocale.current = settings.resolvedLanguage()
    }

    val database: MindTraceDatabase by lazy {
        MindTraceDatabase.create(appContext, keyManager.getOrCreatePassphrase())
    }

    val profileRepository by lazy { ProfileRepository(database.profileDao()) }
    val symptomRepository by lazy { SymptomRepository(database.symptomDao()) }
    val checkInRepository by lazy { CheckInRepository(database.checkInDao()) }
    val medicationRepository by lazy {
        MedicationRepository(database.medicationDao(), database.medicationLogDao())
    }
    val scaleRepository by lazy { ScaleRepository(database.scaleDao()) }
    val journalRepository by lazy { JournalRepository(database.journalDao()) }
    val safetyRepository by lazy { SafetyRepository(database.safetyDao()) }
    val diagnosisRepository by lazy { DiagnosisRepository(database.diagnosisDao()) }

    init {
        appScope.launch {
            val conditions = profileRepository.get()?.conditions
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            symptomRepository.ensureSeeded(conditions.ifEmpty { listOf("unsure") })
        }
    }

    /**
     * 删除数据库、密钥和所有设置。调用后当前进程的数据层不可再使用，应退出应用。
     */
    fun eraseEverything() {
        runCatching { database.close() }
        val dbFile = appContext.getDatabasePath(MindTraceDatabase.DB_NAME)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"),
            File(dbFile.path + "-journal")).forEach { runCatching { it.delete() } }
        keyManager.destroyKey()
        settings.reset()
        appContext.getSharedPreferences("mindtrace_secure", Context.MODE_PRIVATE)
            .edit().clear().commit()
        appContext.getSharedPreferences("mindtrace_alarms", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
