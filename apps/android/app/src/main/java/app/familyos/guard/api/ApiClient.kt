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
import app.familyos.guard.data.Product
import app.familyos.guard.data.Recipe
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

    suspend fun questDetails(questId: String): JSONObject = withContext(Dispatchers.IO) {
        call("GET", "/api/device/quests/$questId/details", null).optJSONObject("data") ?: JSONObject()
    }

    suspend fun startQuest(questId: String): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/device/quests/$questId/start", JSONObject()).optJSONObject("data") ?: JSONObject()
    }

    suspend fun finishQuest(questId: String) = withContext(Dispatchers.IO) {
        call("POST", "/api/device/quests/$questId/finish", JSONObject())
        Unit
    }

    suspend fun scanQuestItem(questId: String, barcode: String): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/device/quests/$questId/scan", JSONObject().put("barcode", barcode))
            .optJSONObject("data") ?: JSONObject()
    }

    suspend fun policySession(): JSONObject? = withContext(Dispatchers.IO) {
        val data = call("GET", "/api/device/policy", null).optJSONObject("data") ?: return@withContext null
        if (data.isNull("session")) null else data.optJSONObject("session")
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

    suspend fun createEvent(
        title: String,
        description: String,
        geofenceId: String?,
        date: String,
        startTime: String,
        durationMin: Int,
        lat: Double? = null,
        lng: Double? = null,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("title", title)
            .put("date", date)
            .put("startTime", startTime)
            .put("durationMin", durationMin)
        if (description.isNotBlank()) body.put("description", description)
        if (geofenceId != null) body.put("geofenceId", geofenceId)
        if (lat != null && lng != null) body.put("lat", lat).put("lng", lng)
        call("POST", "/api/device/child-events", body)
        Unit
    }

    suspend fun recipes(): List<Recipe> = withContext(Dispatchers.IO) {
        Recipe.listFrom(call("GET", "/api/device/recipes", null))
    }

    suspend fun cookRecipe(id: String): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/device/recipes/$id/cook", JSONObject()).optJSONObject("data") ?: JSONObject()
    }

    suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
        Product.listFrom(call("GET", "/api/device/products", null))
    }

    suspend fun scanBarcode(barcode: String): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/device/products/scan", JSONObject().put("barcode", barcode).put("quantity", 1))
            .optJSONObject("data") ?: JSONObject()
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

    suspend fun siteRules(): JSONObject = withContext(Dispatchers.IO) {
        call("GET", "/api/device/sites/blocklist", null).optJSONObject("data") ?: JSONObject()
    }

    suspend fun sendSites(domains: List<String>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        domains.forEach { array.put(it) }
        call("POST", "/api/device/sites", JSONObject().put("domains", array))
        Unit
    }

    suspend fun reportCrash(message: String, stack: String): Unit = withContext(Dispatchers.IO) {
        call(
            "POST",
            "/api/device/crash",
            JSONObject().put("message", message).put("stack", stack.take(8000)).put("appVersion", "0.1.0"),
        )
        Unit
    }

    suspend fun rawCall(method: String, path: String, bodyJson: String): JSONObject = withContext(Dispatchers.IO) {
        val body = if (bodyJson.isBlank()) null else runCatching { JSONObject(bodyJson) }.getOrNull()
        call(method, path, body)
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
