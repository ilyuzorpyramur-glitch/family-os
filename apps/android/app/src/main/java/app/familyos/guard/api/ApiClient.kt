package app.familyos.guard.api

import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.Policy
import app.familyos.guard.data.QuestData
import app.familyos.guard.data.ShopData
import app.familyos.guard.data.ProposalItem
import app.familyos.guard.data.GeofenceDto
import app.familyos.guard.data.HomeData
import app.familyos.guard.data.ProfileData
import app.familyos.guard.data.CalendarData
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

    suspend fun claimQuest(questId: String) = withContext(Dispatchers.IO) {
        call("POST", "/api/device/quests/$questId/claim", JSONObject())
        Unit
    }

    suspend fun completeQuest(questId: String, images: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        if (images.isNotEmpty()) {
            val body = JSONObject().put("images", JSONArray(images))
            call("POST", "/api/device/quests/$questId/photos", body)
        }
        call("POST", "/api/device/quests/$questId/complete", JSONObject())
        Unit
    }

    suspend fun proposeShopItem(title: String, description: String, imageBase64: String?): ProposalItem = withContext(Dispatchers.IO) {
        val body = JSONObject().put("title", title)
        if (description.isNotBlank()) body.put("description", description)
        if (!imageBase64.isNullOrBlank()) body.put("imageBase64", imageBase64)
        ProposalItem.fromJson(call("POST", "/api/device/shop/proposals", body).getJSONObject("data"))
    }

    suspend fun shop(): ShopData = withContext(Dispatchers.IO) {
        ShopData.fromJson(call("GET", "/api/device/shop", null))
    }

    suspend fun buyItem(itemId: String) = withContext(Dispatchers.IO) {
        call("POST", "/api/device/shop/$itemId/buy", JSONObject())
        Unit
    }

    suspend fun home(): HomeData = withContext(Dispatchers.IO) {
        HomeData.fromJson(call("GET", "/api/device/home", null))
    }

    suspend fun calendar(month: String): CalendarData = withContext(Dispatchers.IO) {
        CalendarData.fromJson(call("GET", "/api/device/calendar?month=$month", null))
    }

    suspend fun createEvent(title: String, geofenceId: String?, date: String, startTime: String, durationMin: Int) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("title", title).put("date", date).put("startTime", startTime).put("durationMin", durationMin)
        if (geofenceId != null) body.put("geofenceId", geofenceId)
        call("POST", "/api/device/child-events", body)
        Unit
    }

    suspend fun profile(): ProfileData = withContext(Dispatchers.IO) {
        ProfileData.fromJson(call("GET", "/api/device/profile", null))
    }

    suspend fun uploadAvatar(imageBase64: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("imageBase64", imageBase64)
        call("POST", "/api/device/profile/avatar", body).getJSONObject("data").optString("avatarUrl")
    }

    suspend fun updateProfile(nickname: String?, school: String?, grade: String?) = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (nickname != null) body.put("nickname", nickname)
        if (school != null) body.put("school", school)
        if (grade != null) body.put("grade", grade)
        call("PATCH", "/api/device/profile", body)
        Unit
    }

    suspend fun sendLocation(lat: Double, lng: Double, accuracy: Float?) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("lat", lat).put("lng", lng)
        if (accuracy != null) body.put("accuracy", accuracy.toDouble())
        call("POST", "/api/device/location", body)
        Unit
    }

    suspend fun geofences(): List<GeofenceDto> = withContext(Dispatchers.IO) {
        val data = call("GET", "/api/device/geofences", null)
        val arr = data.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).map { GeofenceDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun geofenceEvent(geofenceId: String, type: String) = withContext(Dispatchers.IO) {
        call(
            "POST",
            "/api/device/geofences/event",
            JSONObject().put("geofenceId", geofenceId).put("type", type),
        )
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
