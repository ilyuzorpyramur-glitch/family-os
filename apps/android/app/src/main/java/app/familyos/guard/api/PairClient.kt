package app.familyos.guard.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Обмен QR-кода на токен. Работает без авторизации. */
object PairClient {

    data class Claimed(
        val role: String,
        val token: String,
        val serverUrl: String,
        val childName: String?,
    )

    suspend fun claim(baseUrl: String, code: String, deviceName: String? = null): Claimed =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isBlank()) throw IOException("Не задан адрес сервера")

            val body = JSONObject().put("code", code)
            if (!deviceName.isNullOrBlank()) body.put("deviceName", deviceName)

            val connection = URL("$base/api/pair/claim").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IOException(friendlyError(text, status))
                }
                val data = JSONObject(text).optJSONObject("data") ?: JSONObject()
                Claimed(
                    role = data.optString("role"),
                    token = data.optString("token"),
                    serverUrl = data.optString("serverUrl", base),
                    childName = data.optString("childName").takeIf { it.isNotBlank() },
                )
            } finally {
                connection.disconnect()
            }
        }

    /** Проверить адрес: там ли Family OS. */
    suspend fun info(baseUrl: String): JSONObject = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        val connection = URL("$base/api/pair/info").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val status = connection.responseCode
            val text = connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Сервер ответил $status")
            JSONObject(text).optJSONObject("data") ?: JSONObject()
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(text: String, status: Int): String {
        val code = runCatching { JSONObject(text).optJSONObject("error")?.optString("code") }.getOrNull()
        return when (code) {
            "PAIR_INVALID" -> "Код неверный или истёк — попросите новый QR"
            "NOT_FOUND" -> "Код не привязан к ребёнку"
            else -> "Ошибка $status: ${text.take(160)}"
        }
    }
}
