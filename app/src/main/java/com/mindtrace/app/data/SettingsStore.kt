package com.mindtrace.app.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 非敏感运行设置。个人资料、症状、病种等敏感数据都放在加密数据库里。
 * PIN 只保存 PBKDF2 哈希；它是一道界面门禁，真正保护数据的是 SQLCipher + Keystore。
 */
data class UiSettings(
    val onboardingComplete: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 30,
    val medicationRemindersEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val preventScreenshots: Boolean = false,
    val stealthNotifications: Boolean = true,
    val autoLockSeconds: Int = 60,
    val hasPin: Boolean = false,
    val hasAnyUnlock: Boolean = false,
    val defaultReportDays: Int = 30,
    val language: String = "system"
)

class SettingsStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("mindtrace_settings", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = sp.getBoolean("onboarding_complete", false)
        set(value) = sp.edit().putBoolean("onboarding_complete", value).apply()

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer_accepted", false)
        set(value) = sp.edit().putBoolean("disclaimer_accepted", value).apply()

    var dailyReminderEnabled: Boolean
        get() = sp.getBoolean("daily_reminder_enabled", false)
        set(value) = sp.edit().putBoolean("daily_reminder_enabled", value).apply()

    var dailyReminderHour: Int
        get() = sp.getInt("daily_reminder_hour", 20)
        set(value) = sp.edit().putInt("daily_reminder_hour", value.coerceIn(0, 23)).apply()

    var dailyReminderMinute: Int
        get() = sp.getInt("daily_reminder_minute", 30)
        set(value) = sp.edit().putInt("daily_reminder_minute", value.coerceIn(0, 59)).apply()

    var medicationRemindersEnabled: Boolean
        get() = sp.getBoolean("med_reminders_enabled", true)
        set(value) = sp.edit().putBoolean("med_reminders_enabled", value).apply()

    var appLockEnabled: Boolean
        get() = sp.getBoolean("app_lock_enabled", false)
        set(value) = sp.edit().putBoolean("app_lock_enabled", value).apply()

    var biometricEnabled: Boolean
        get() = sp.getBoolean("biometric_enabled", false)
        set(value) = sp.edit().putBoolean("biometric_enabled", value).apply()

    var preventScreenshots: Boolean
        get() = sp.getBoolean("prevent_screenshots", false)
        set(value) = sp.edit().putBoolean("prevent_screenshots", value).apply()

    var stealthNotifications: Boolean
        get() = sp.getBoolean("stealth_notifications", true)
        set(value) = sp.edit().putBoolean("stealth_notifications", value).apply()

    var autoLockSeconds: Int
        get() = sp.getInt("auto_lock_seconds", 60)
        set(value) = sp.edit().putInt("auto_lock_seconds", value.coerceIn(0, 3600)).apply()

    var defaultReportDays: Int
        get() = sp.getInt("default_report_days", 30)
        set(value) = sp.edit().putInt("default_report_days", value).apply()

    var lastScalePromptAt: Long
        get() = sp.getLong("last_scale_prompt_at", 0L)
        set(value) = sp.edit().putLong("last_scale_prompt_at", value).apply()

    /**
     * 语言覆盖设置。system 表示跟随系统；一旦用户手动选择就固定下来。
     */
    var language: String
        get() = sp.getString("app_language", "system") ?: "system"
        set(value) {
            sp.edit().putString("app_language", value).apply()
            AppLocale.current = AppLocale.fromCode(value)
        }

    fun resolvedLanguage(): AppLanguage = AppLocale.fromCode(language)

    val hasPin: Boolean
        get() = !sp.getString("pin_hash", null).isNullOrBlank()

    val hasAnyUnlock: Boolean
        get() = hasPin || biometricEnabled

    fun snapshot(): UiSettings = UiSettings(
        onboardingComplete = onboardingComplete,
        disclaimerAccepted = disclaimerAccepted,
        dailyReminderEnabled = dailyReminderEnabled,
        dailyReminderHour = dailyReminderHour,
        dailyReminderMinute = dailyReminderMinute,
        medicationRemindersEnabled = medicationRemindersEnabled,
        appLockEnabled = appLockEnabled,
        biometricEnabled = biometricEnabled,
        preventScreenshots = preventScreenshots,
        stealthNotifications = stealthNotifications,
        autoLockSeconds = autoLockSeconds,
        hasPin = hasPin,
        hasAnyUnlock = hasAnyUnlock,
        defaultReportDays = defaultReportDays,
        language = language
    )

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt, PIN_ITERATIONS)
        sp.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("pin_iterations", PIN_ITERATIONS)
            .putBoolean("app_lock_enabled", true)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltText = sp.getString("pin_salt", null) ?: return false
        val hashText = sp.getString("pin_hash", null) ?: return false
        val iterations = sp.getInt("pin_iterations", PIN_ITERATIONS)
        return runCatching {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hashPin(pin, salt, iterations))
        }.getOrDefault(false)
    }

    fun clearPin() {
        sp.edit()
            .remove("pin_salt")
            .remove("pin_hash")
            .remove("pin_iterations")
            .remove("biometric_enabled")
            .putBoolean("app_lock_enabled", false)
            .apply()
    }

    fun reset() {
        // 必须同步写入：删除全部数据后会立即结束进程，apply() 的异步落盘可能丢失。
        sp.edit().clear().commit()
    }

    private fun hashPin(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val PIN_ITERATIONS = 120_000
    }
}
