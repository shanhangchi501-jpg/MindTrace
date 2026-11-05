package com.mindtrace.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 云备份目标平台。 */
enum class CloudProvider(val key: String, val label: String) {
    WEBDAV("webdav", "WebDAV"),
    GITEE("gitee", "Gitee"),
    GITHUB("github", "GitHub");

    companion object {
        fun fromKey(key: String?): CloudProvider =
            values().firstOrNull { it.key == key } ?: WEBDAV
    }
}

data class CloudConfig(
    val provider: CloudProvider = CloudProvider.WEBDAV,
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val gitOwner: String = "",
    val gitRepo: String = "",
    val gitBranch: String = "main",
    val gitPath: String = DEFAULT_PATH,
    val gitToken: String = ""
) {
    val ready: Boolean
        get() = if (provider == CloudProvider.WEBDAV) {
            webdavUrl.isNotBlank() && webdavUser.isNotBlank() && webdavPassword.isNotBlank()
        } else {
            gitOwner.isNotBlank() && gitRepo.isNotBlank() &&
                gitToken.isNotBlank() && gitPath.isNotBlank()
        }

    companion object {
        const val DEFAULT_PATH = "MindTrace/mindtrace.mtrbackup"
    }
}

/**
 * 云备份连接配置。非敏感字段放在应用私有 SharedPreferences；
 * WebDAV 密码与 Git 令牌用 Android Keystore 中不可导出的 AES-GCM 密钥加密后保存。
 * 加密备份文件本身的密码从不保存。
 */
class CloudBackupStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("mindtrace_cloud", Context.MODE_PRIVATE)

    fun load(): CloudConfig = CloudConfig(
        provider = CloudProvider.fromKey(sp.getString("provider", null)),
        webdavUrl = sp.getString("webdav_url", "") ?: "",
        webdavUser = sp.getString("webdav_user", "") ?: "",
        webdavPassword = decrypt(sp.getString("webdav_password", null)),
        gitOwner = sp.getString("git_owner", "") ?: "",
        gitRepo = sp.getString("git_repo", "") ?: "",
        gitBranch = sp.getString("git_branch", "main") ?: "main",
        gitPath = sp.getString("git_path", CloudConfig.DEFAULT_PATH) ?: CloudConfig.DEFAULT_PATH,
        gitToken = decrypt(sp.getString("git_token", null))
    )

    fun save(config: CloudConfig) {
        sp.edit()
            .putString("provider", config.provider.key)
            .putString("webdav_url", config.webdavUrl.trim())
            .putString("webdav_user", config.webdavUser.trim())
            .putString("webdav_password", encrypt(config.webdavPassword))
            .putString("git_owner", config.gitOwner.trim())
            .putString("git_repo", config.gitRepo.trim())
            .putString("git_branch", config.gitBranch.trim().ifBlank { "main" })
            .putString("git_path", config.gitPath.trim().ifBlank { CloudConfig.DEFAULT_PATH })
            .putString("git_token", encrypt(config.gitToken))
            .apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    private fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return runCatching {
            val parts = stored.split(':', limit = 2)
            require(parts.size == 2) { "损坏的凭据" }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mindtrace_cloud_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
