package com.mindtrace.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 云备份传输层。
 *
 * 只负责把加密后的 .mtrbackup 字节上传 / 下载，不理解备份内容，也不保存密码。
 * 全部使用 JDK 自带的 HttpURLConnection（HTTPS），不引入第三方网络库。
 *
 * - WebDAV：PUT 上传、GET 下载，Basic 认证；目录不存在时尝试 MKCOL。
 * - Gitee：/api/v5/repos/{owner}/{repo}/contents/{path}，POST 新建 / PUT 更新。
 * - GitHub：/repos/{owner}/{repo}/contents/{path}，PUT 创建或更新。
 */
object CloudBackupClient {

    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 60_000
    private const val MAX_DOWNLOAD = 64 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upload(config: CloudConfig, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.ready) { "请先填写并保存云备份配置" }
                when (config.provider) {
                    CloudProvider.WEBDAV -> {
                        uploadWebdav(config, bytes)
                        "WebDAV"
                    }
                    else -> {
                        uploadGit(config, bytes)
                        config.provider.label
                    }
                }
            }
        }

    suspend fun download(config: CloudConfig): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.ready) { "请先填写并保存云备份配置" }
                when (config.provider) {
                    CloudProvider.WEBDAV -> downloadWebdav(config)
                    else -> downloadGit(config)
                }
            }
        }

    // ---------------- WebDAV ----------------

    private fun webdavTarget(config: CloudConfig): String {
        val url = config.webdavUrl.trim()
        require(url.startsWith("https://", ignoreCase = true)) {
            "WebDAV 地址需要使用 https:// 开头，并包含备份文件名"
        }
        return url
    }

    private fun basicAuth(config: CloudConfig): String =
        "Basic " + Base64.encodeToString(
            "${config.webdavUser.trim()}:${config.webdavPassword}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

    private fun parentUrl(raw: String): String {
        val url = URL(raw)
        val path = url.path.trimEnd('/')
        val index = path.lastIndexOf('/')
        if (index <= 0) return raw
        return URL(url.protocol, url.host, url.port, path.substring(0, index + 1)).toString()
    }

    private fun uploadWebdav(config: CloudConfig, bytes: ByteArray) {
        val target = webdavTarget(config)
        // 目录不存在时尝试创建；已存在（405）或服务不支持时忽略，随后直接 PUT。
        runCatching {
            val conn = connection(parentUrl(target), "MKCOL").apply {
                setRequestProperty("Authorization", basicAuth(config))
            }
            conn.responseCode
            conn.disconnect()
        }
        val conn = connection(target, "PUT").apply {
            doOutput = true
            setRequestProperty("Authorization", basicAuth(config))
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Length", bytes.size.toString())
        }
        conn.outputStream.use { it.write(bytes) }
        conn.readResult()
    }

    private fun downloadWebdav(config: CloudConfig): ByteArray {
        val conn = connection(webdavTarget(config), "GET").apply {
            setRequestProperty("Authorization", basicAuth(config))
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw IllegalStateException("下载失败：HTTP $code ${body.take(200)}")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        require(bytes.size in 1..MAX_DOWNLOAD) { "云端备份文件为空或过大" }
        return bytes
    }

    // ---------------- Gitee / GitHub ----------------

    private fun gitEndpoint(config: CloudConfig): String {
        val base = if (config.provider == CloudProvider.GITEE) {
            "https://gitee.com/api/v5/repos"
        } else {
            "https://api.github.com/repos"
        }
        val owner = enc(config.gitOwner.trim())
        val repo = enc(config.gitRepo.trim())
        val path = config.gitPath.trim().trim('/').split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { enc(it) }
        require(owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()) {
            "仓库所有者、仓库名和文件路径不能为空"
        }
        return "$base/$owner/$repo/contents/$path"
    }

    private fun gitGetUrl(config: CloudConfig): String {
        val endpoint = gitEndpoint(config)
        val ref = "ref=${enc(config.gitBranch.trim().ifBlank { "main" })}"
        return if (config.provider == CloudProvider.GITEE) {
            "$endpoint?access_token=${enc(config.gitToken.trim())}&$ref"
        } else {
            "$endpoint?$ref"
        }
    }

    private fun gitWriteUrl(config: CloudConfig): String {
        val endpoint = gitEndpoint(config)
        return if (config.provider == CloudProvider.GITEE) {
            "$endpoint?access_token=${enc(config.gitToken.trim())}"
        } else {
            endpoint
        }
    }

    private fun gitHeaders(config: CloudConfig, conn: HttpURLConnection) {
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MindTrace-CloudBackup")
        if (config.provider == CloudProvider.GITHUB) {
            conn.setRequestProperty("Authorization", "Bearer ${config.gitToken.trim()}")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    /** 读取仓库中现有文件的 sha 与内容；文件不存在返回 (null, null)。 */
    private fun readExisting(config: CloudConfig): Pair<String?, ByteArray?> {
        val conn = connection(gitGetUrl(config), "GET")
        gitHeaders(config, conn)
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code == 404) return null to null
        if (code !in 200..299) {
            throw IllegalStateException("读取仓库文件失败：HTTP $code ${body.take(200)}")
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
            throw IllegalStateException("仓库返回的内容不是预期的文件信息")
        }
        val sha = root["sha"]?.jsonPrimitive?.contentOrNull
        val encoded = root["content"]?.jsonPrimitive?.contentOrNull
        val decoded = encoded?.let {
            runCatching {
                Base64.decode(it.replace("\n", "").replace("\r", ""), Base64.NO_WRAP)
            }.getOrNull()
        }
        if (decoded != null) return sha to decoded

        // GitHub 对大文件不返回 content 字段，改用 download_url 取原始内容。
        val rawUrl = root["download_url"]?.jsonPrimitive?.contentOrNull
        if (!rawUrl.isNullOrBlank()) {
            return sha to runCatching { downloadRaw(config, rawUrl) }.getOrNull()
        }
        return sha to null
    }

    private fun uploadGit(config: CloudConfig, bytes: ByteArray) {
        val (sha, _) = readExisting(config)
        val method = when {
            config.provider == CloudProvider.GITHUB -> "PUT"
            sha.isNullOrBlank() -> "POST" // Gitee 新建文件
            else -> "PUT"                 // Gitee 更新文件
        }
        val requestBody = buildJsonObject {
            put("message", "MindTrace encrypted backup ${System.currentTimeMillis()}")
            put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("branch", config.gitBranch.trim().ifBlank { "main" })
            if (!sha.isNullOrBlank()) put("sha", sha)
        }.toString()

        val conn = connection(gitWriteUrl(config), method).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            gitHeaders(config, this)
        }
        conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        require(code in 200..299) { "上传失败：HTTP $code ${response.take(200)}" }
    }

    private fun downloadGit(config: CloudConfig): ByteArray {
        val (_, bytes) = readExisting(config)
        val result = bytes
            ?: throw IllegalStateException("云端没有找到备份文件，或文件内容无法读取")
        require(result.size in 1..MAX_DOWNLOAD) { "云端备份文件为空或过大" }
        return result
    }

    private fun downloadRaw(config: CloudConfig, url: String): ByteArray {
        val conn = connection(url, "GET")
        gitHeaders(config, conn)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("下载原始文件失败：HTTP $code")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        return bytes
    }

    // ---------------- 通用 ----------------

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun connection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MindTrace-CloudBackup")
        }

    private fun HttpURLConnection.readResult(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code ${body.take(200)}")
        }
        return body
    }
}
