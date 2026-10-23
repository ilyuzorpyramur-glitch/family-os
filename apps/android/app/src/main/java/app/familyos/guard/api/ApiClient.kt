package app.familyos.guard.api

import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.Policy
import app.familyos.guard.data.QuestData
import app.familyos.guard.data.ShopData
import app.familyos.guard.data.UsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val store: GuardStore) {

    suspend fun refreshPolicy(): Policy = withContext(Dispatchers.IO) {
        val data = call("GET", "/api/device/policy", null).getJSONObject("data")
        store.savePolicyBody(data.toString())
        Policy.fromJson(data.toString())
    }

    suspend fun sendUsage(snapshot: UsageCollector.Snapshot) = withContext(Dispatchers.IO) {
        val apps = JSONArray()
        snapshot.apps.forEach { app ->
            apps.put(
                JSONObject()
                    .put("packageId", app.packageId)
                    .put("label", app.label)
                    .put("minutes", app.minutes),
            )
        }
        val body = JSONObject()
            .put("minutes", snapshot.totalMinutes)
            .put("apps", apps)
        call("POST", "/api/device/usage", body)
        Unit
    }

    suspend fun heartbeat(): Int = withContext(Dispatchers.IO) {
        val data = call("POST", "/api/device/heartbeat", JSONObject()).getJSONObject("data")
        data.optInt("limitMinutes", 0)
    }

    suspend fun requestExtension(minutes: Int, reason: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("minutes", minutes).put("reason", reason)
        val response = call("POST", "/api/device/extension-request", body)
        response.optJSONObject("data")?.optString("id").orEmpty()
    }

    suspend fun quests(): QuestData = withContext(Dispatchers.IO) {
        QuestData.fromJson(call("GET", "/api/device/quests", null))
    }

    suspend fun completeQuest(questId: String) = withContext(Dispatchers.IO) {
        call("POST", "/api/device/quests/$questId/complete", JSONObject())
        Unit
    }

    suspend fun shop(): ShopData = withContext(Dispatchers.IO) {
        ShopData.fromJson(call("GET", "/api/device/shop", null))
    }

    suspend fun buyItem(itemId: String) = withContext(Dispatchers.IO) {
        call("POST", "/api/device/shop/$itemId/buy", JSONObject())
        Unit
    }

    suspend fun sendEvent(kind: String, payload: JSONObject? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("kind", kind)
        if (payload != null) body.put("payload", payload)
        call("POST", "/api/device/events", body)
        Unit
    }

    private fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val base = store.serverUrl.trimEnd('/')
        if (base.isBlank()) throw IOException("Не задан адрес сервера")
        if (store.token.isBlank()) throw IOException("Не задан токен устройства")

        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer " + store.token)
            connection.setRequestProperty("Accept", "application/json")

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(300)}")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
