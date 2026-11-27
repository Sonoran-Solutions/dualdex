package com.dualdex.retroachievements

import com.dualdex.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

internal class RaHttpRequest(
    val id: Long,
    val url: String,
    val postData: ByteArray?,
    val contentType: String?
) {
    override fun toString() = "RaHttpRequest(id=$id, data=[redacted])"
}

internal class RaHttpResponse(val status: Int, val body: ByteArray) {
    override fun toString() = "RaHttpResponse(status=$status, body=[redacted])"
}

internal object RaHttp {
    private const val CLIENT_ERROR = -1
    private const val RETRYABLE_CLIENT_ERROR = -2
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    private const val TIMEOUT_MILLIS = 8000

    /** Called only on the IO dispatcher. Redirects may otherwise send a credential off-site. */
    fun execute(
        request: RaHttpRequest,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
    ): RaHttpResponse {
        val uri = runCatching { URI(request.url) }.getOrNull()
        if (uri?.scheme != "https" || uri.host != "retroachievements.org" ||
            (uri.port != -1 && uri.port != 443) || uri.rawUserInfo != null || uri.rawFragment != null ||
            uri.rawPath != "/dorequest.php"
        ) {
            return failure(CLIENT_ERROR, "RetroAchievements endpoint refused")
        }

        return try {
            val connection = open(uri.toURL())
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "DualDex/${BuildConfig.VERSION_NAME} rcheevos/12.5.0")
                request.postData?.let { body ->
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", request.contentType ?: "application/x-www-form-urlencoded")
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }

                val status = connection.responseCode
                when {
                    status == 408 || status == 429 || status >= 500 ->
                        failure(RETRYABLE_CLIENT_ERROR, "RetroAchievements is temporarily unavailable")
                    status < 200 || status in 300..399 ->
                        failure(CLIENT_ERROR, "RetroAchievements response refused")
                    else -> {
                        val stream = if (status >= 400) connection.errorStream else connection.inputStream
                        val body = stream?.use {
                            val buffer = ByteArray(8192)
                            val output = ByteArrayOutputStream()
                            while (true) {
                                if (System.nanoTime() - deadline >= 0) throw IOException()
                                val count = it.read(buffer)
                                if (count < 0) break
                                if (output.size() > MAX_BODY_BYTES - count) {
                                    return failure(CLIENT_ERROR, "RetroAchievements response too large")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        if (body == null || body.isEmpty()) {
                            if (status == 401 || status == 403) {
                                RaHttpResponse(status, """{"Success":false,"Error":"Login rejected","Code":"access_denied"}""".encodeToByteArray())
                            } else {
                                failure(RETRYABLE_CLIENT_ERROR, "RetroAchievements response unavailable")
                            }
                        } else {
                            RaHttpResponse(status, body)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            failure(RETRYABLE_CLIENT_ERROR, "RetroAchievements connection unavailable")
        } catch (_: SecurityException) {
            failure(CLIENT_ERROR, "RetroAchievements connection refused")
        } catch (_: IllegalArgumentException) {
            failure(CLIENT_ERROR, "RetroAchievements request refused")
        }
    }

    private fun failure(status: Int, message: String) = RaHttpResponse(status, message.encodeToByteArray())
}
