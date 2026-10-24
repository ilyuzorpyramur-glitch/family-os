package app.familyos.guard.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Клиент родительской панели: работает по admin-токену. */
class ParentApi(var baseUrl: String, var token: String) {

    suspend fun bootstrap(): JSONObject = get("/api/guard/bootstrap").optJSONObject("data") ?: JSONObject()

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) { call("GET", path, null) }

    suspend fun post(path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) { call("POST", path, body) }

    suspend fun patch(path: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) { call("PATCH", path, body) }

    private fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IOException("Не задан адрес сервера")
        if (token.isBlank()) throw IOException("Не задан токен родителя")

        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
