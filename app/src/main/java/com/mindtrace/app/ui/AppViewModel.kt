package com.mindtrace.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindtrace.app.MindTraceApp
import com.mindtrace.app.data.AdherenceSummary
import com.mindtrace.app.data.AppLanguage
import com.mindtrace.app.data.BackupManager
import com.mindtrace.app.data.CheckInEntity
import com.mindtrace.app.data.CrisisContactEntity
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.data.DiagnosisEntity
import com.mindtrace.app.data.DoseSlot
import com.mindtrace.app.data.DoseStatus
import com.mindtrace.app.data.JournalEntryEntity
import com.mindtrace.app.data.MedicationEntity
import com.mindtrace.app.data.ProfileEntity
import com.mindtrace.app.data.SafetyPlanEntity
import com.mindtrace.app.data.ScaleAssessmentEntity
import com.mindtrace.app.data.ScaleCatalog
import com.mindtrace.app.data.SymptomEntity
import com.mindtrace.app.data.UiSettings
import com.mindtrace.app.report.ReportBuilder
import com.mindtrace.app.report.ReportData
import com.mindtrace.app.report.ReportOptions
import com.mindtrace.app.report.ReportPdfGenerator
import com.mindtrace.app.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MindTraceApp).container
    private val settingsStore = container.settings

    private val _settings = MutableStateFlow(settingsStore.snapshot())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    val profile: StateFlow<ProfileEntity?> = container.profileRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val symptoms: StateFlow<List<SymptomEntity>> = container.symptomRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkIns: StateFlow<List<CheckInEntity>> = container.checkInRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val medications: StateFlow<List<MedicationEntity>> = container.medicationRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMedications: StateFlow<List<MedicationEntity>> = container.medicationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scales: StateFlow<List<ScaleAssessmentEntity>> = container.scaleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val journal: StateFlow<List<JournalEntryEntity>> = container.journalRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val safetyPlan: StateFlow<SafetyPlanEntity?> = container.safetyRepository.observePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contacts: StateFlow<List<CrisisContactEntity>> = container.safetyRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diagnoses: StateFlow<List<DiagnosisEntity>> = container.diagnosisRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val today: StateFlow<String> = MutableStateFlow(DateUtils.todayIso()).asStateFlow()

    val todayLogs = today.flatMapLatest { date ->
        container.medicationRepository.observeLogsForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val insightsDays = MutableStateFlow(settingsStore.defaultReportDays)
    val insightsCheckIns = insightsDays.flatMapLatest { days ->
        val (start, end) = DateUtils.rangeForDays(days)
        container.checkInRepository.observeRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val insightsLogs = insightsDays.flatMapLatest { days ->
        val (start, end) = DateUtils.rangeForDays(days)
        container.medicationRepository.observeLogs(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var locked by mutableStateOf(
        settingsStore.onboardingComplete &&
            settingsStore.appLockEnabled &&
            settingsStore.hasAnyUnlock
    )
        private set

    private var backgroundedAt: Long = 0L

    init {
        viewModelScope.launch {
            container.symptomRepository.ensureSeeded()
            syncReminders()
        }
    }

    fun refreshSettings() {
        _settings.value = settingsStore.snapshot()
    }

    fun lockNow() {
        if (settingsStore.appLockEnabled && settingsStore.hasAnyUnlock) {
            locked = true
        }
    }

    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (locked) return
        if (!settingsStore.appLockEnabled || !settingsStore.hasAnyUnlock) return
        val timeout = settingsStore.autoLockSeconds * 1000L
        if (timeout <= 0L || (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt >= timeout)) {
            locked = true
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val ok = settingsStore.verifyPin(pin)
        if (ok) locked = false
        return ok
    }

    fun unlockWithBiometric() {
        locked = false
    }

    fun completeOnboarding(
        displayName: String,
        conditions: List<String>,
        enabledSymptomIds: Set<String>,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        pin: String?,
        biometric: Boolean
    ) {
        viewModelScope.launch {
            container.profileRepository.update(
                displayName = displayName.trim(),
                conditions = conditions
            )
            container.symptomRepository.ensureSeeded()
            container.symptomRepository.setEnabledSymptoms(
                enabledSymptomIds.ifEmpty { com.mindtrace.app.data.SymptomCatalog.suggestedFor(conditions) }
            )
            settingsStore.onboardingComplete = true
            settingsStore.disclaimerAccepted = true
            settingsStore.dailyReminderEnabled = reminderEnabled
            settingsStore.dailyReminderHour = reminderHour
            settingsStore.dailyReminderMinute = reminderMinute
            if (!pin.isNullOrBlank()) {
                settingsStore.setPin(pin)
                settingsStore.appLockEnabled = true
                settingsStore.biometricEnabled = biometric
            } else {
                settingsStore.appLockEnabled = false
                settingsStore.biometricEnabled = false
            }
            refreshSettings()
            locked = false
            syncReminders()
        }
    }

    fun updateProfile(
        displayName: String,
        birthYear: Int?,
        gender: String,
        conditions: List<String>,
        note: String
    ) {
        viewModelScope.launch {
            container.profileRepository.update(
                displayName = displayName,
                birthYear = birthYear,
                gender = gender,
                conditions = conditions,
                note = note
            )
            val keys = conditions.ifEmpty { listOf("unsure") }
            container.symptomRepository.ensureSeeded(keys)
            container.symptomRepository.applySuggestedConditions(keys)
        }
    }

    fun saveCheckIn(entity: CheckInEntity) {
        viewModelScope.launch { container.checkInRepository.save(entity) }
    }

    fun deleteCheckIn(entity: CheckInEntity) {
        viewModelScope.launch { container.checkInRepository.delete(entity) }
    }

    fun setSymptomEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { container.symptomRepository.setEnabled(id, enabled) }
    }

    fun addCustomSymptom(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.symptomRepository.addCustom(name) }
    }

    fun deleteCustomSymptom(id: String) {
        viewModelScope.launch { container.symptomRepository.deleteCustom(id) }
    }

    fun saveMedication(medication: MedicationEntity) {
        viewModelScope.launch {
            container.medicationRepository.save(medication)
            syncReminders()
        }
    }

    fun deleteMedication(medication: MedicationEntity) {
        viewModelScope.launch {
            container.medicationRepository.delete(medication)
            syncReminders()
        }
    }

    fun setMedicationActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            container.medicationRepository.setActive(id, active)
            syncReminders()
        }
    }

    fun logDose(medicationId: Long, date: String, time: String, status: DoseStatus) {
        viewModelScope.launch {
            container.medicationRepository.log(medicationId, date, time, status)
        }
    }

    fun clearDose(medicationId: Long, date: String, time: String) {
        viewModelScope.launch {
            container.medicationRepository.clearLog(medicationId, date, time)
        }
    }

    fun deleteMedicationLog(id: Long) {
        viewModelScope.launch {
            container.medicationRepository.deleteLog(id)
        }
    }

    suspend fun dosesFor(date: String): List<DoseSlot> {
        val meds = container.medicationRepository.getActive()
        val logs = container.medicationRepository.getAllLogs().filter { it.date == date }
        return container.medicationRepository.dosesFor(date, meds, logs)
    }

    suspend fun adherenceFor(days: Int): AdherenceSummary {
        val (start, end) = DateUtils.rangeForDays(days)
        return container.medicationRepository.adherence(start, end)
    }

    suspend fun submitScale(type: String, answers: List<Int>): ScaleAssessmentEntity {
        return container.scaleRepository.save(type, answers)
    }

    fun deleteScale(id: Long) {
        viewModelScope.launch { container.scaleRepository.delete(id) }
    }

    suspend fun saveJournal(entry: JournalEntryEntity): Long =
        container.journalRepository.save(entry)

    fun deleteJournal(entry: JournalEntryEntity) {
        viewModelScope.launch { container.journalRepository.delete(entry) }
    }

    fun saveSafetyPlan(plan: SafetyPlanEntity) {
        viewModelScope.launch { container.safetyRepository.savePlan(plan) }
    }

    fun saveContact(contact: CrisisContactEntity) {
        viewModelScope.launch { container.safetyRepository.saveContact(contact) }
    }

    fun deleteContact(contact: CrisisContactEntity) {
        viewModelScope.launch { container.safetyRepository.deleteContact(contact) }
    }

    fun saveDiagnosis(diagnosis: DiagnosisEntity) {
        viewModelScope.launch { container.diagnosisRepository.save(diagnosis) }
    }

    fun deleteDiagnosis(diagnosis: DiagnosisEntity) {
        viewModelScope.launch { container.diagnosisRepository.delete(diagnosis) }
    }

    fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
        settingsStore.dailyReminderEnabled = enabled
        settingsStore.dailyReminderHour = hour
        settingsStore.dailyReminderMinute = minute
        refreshSettings()
        syncReminders()
    }

    fun setMedicationReminders(enabled: Boolean) {
        settingsStore.medicationRemindersEnabled = enabled
        refreshSettings()
        syncReminders()
    }

    fun setStealthNotifications(enabled: Boolean) {
        settingsStore.stealthNotifications = enabled
        refreshSettings()
    }

    fun setPreventScreenshots(enabled: Boolean) {
        settingsStore.preventScreenshots = enabled
        refreshSettings()
    }

    fun setAutoLockSeconds(seconds: Int) {
        settingsStore.autoLockSeconds = seconds
        refreshSettings()
    }

    fun setDefaultReportDays(days: Int) {
        settingsStore.defaultReportDays = days
        insightsDays.value = days
        refreshSettings()
    }

    fun setLanguage(code: String) {
        settingsStore.language = code
        runCatching { com.mindtrace.app.work.NotificationHelper.createChannels(getApplication()) }
        refreshSettings()
    }

    fun setPin(pin: String) {
        settingsStore.setPin(pin)
        settingsStore.appLockEnabled = true
        refreshSettings()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !settingsStore.hasPin) return
        settingsStore.biometricEnabled = enabled
        refreshSettings()
    }

    fun disableAppLock() {
        settingsStore.clearPin()
        refreshSettings()
        locked = false
    }

    suspend fun buildReport(start: String, end: String, options: ReportOptions): ReportData =
        withContext(Dispatchers.IO) { ReportBuilder.build(container, start, end, options) }

    suspend fun writeReportPdf(data: ReportData, output: OutputStream, language: AppLanguage) {
        withContext(Dispatchers.IO) {
            output.use { ReportPdfGenerator.write(data, it, language) }
        }
    }

    suspend fun writeCsv(output: OutputStream, language: AppLanguage) {
        withContext(Dispatchers.IO) {
            output.use { BackupManager.writeCsv(container, it, language) }
        }
    }

    suspend fun writeEncryptedBackup(output: OutputStream, password: CharArray) {
        withContext(Dispatchers.IO) {
            output.use { BackupManager.writeEncrypted(container, it, password) }
        }
    }

    suspend fun restoreEncryptedBackup(input: InputStream, password: CharArray): Result<Unit> {
        val result = withContext(Dispatchers.IO) {
            input.use { BackupManager.restoreEncrypted(container, it, password) }
        }
        if (result.isSuccess) {
            runCatching {
                ReminderScheduler.sync(
                    getApplication(),
                    settingsStore.snapshot(),
                    container.medicationRepository.getActive()
                )
            }
        }
        return result
    }

    fun scaleDef(type: String) = ScaleCatalog.byType(type)

    fun setInsightsDays(days: Int) {
        insightsDays.value = days
    }

    fun eraseAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ReminderScheduler.cancelAll(getApplication())
                androidx.core.app.NotificationManagerCompat.from(getApplication()).cancelAll()
                container.eraseEverything()
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun syncReminders() {
        viewModelScope.launch {
            runCatching {
                val meds = container.medicationRepository.getActive()
                ReminderScheduler.sync(getApplication(), settingsStore.snapshot(), meds)
            }
        }
    }
}
