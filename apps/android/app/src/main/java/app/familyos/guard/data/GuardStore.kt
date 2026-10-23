package app.familyos.guard.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalTime

class GuardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var policyJson: String?
        get() = prefs.getString(KEY_POLICY, null)
        set(value) = prefs.edit().putString(KEY_POLICY, value).apply()

    var parentToken: String
        get() = prefs.getString(KEY_PARENT_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PARENT_TOKEN, value.trim()).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()

    fun policy(): Policy? = policyJson?.let { raw ->
        runCatching { Policy.fromJson(raw) }.getOrNull()
    }

    fun configure(url: String, deviceKey: String) {
        prefs.edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .putString(KEY_TOKEN, deviceKey.trim())
            .apply()
    }

    fun savePolicyBody(body: String) {
        policyJson = body
        prefs.edit().putLong(KEY_POLICY_AT, System.currentTimeMillis()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_POLICY = "policy_json"
        private const val KEY_POLICY_AT = "policy_at"
        private const val KEY_PARENT_TOKEN = "parent_token"
    }
}

data class Policy(
    val childName: String,
    val limitMinutes: Int,
    val usedMinutes: Int,
    val remainMinutes: Int,
    val bedtimeStart: String,
    val bedtimeEnd: String,
    val webFilter: Boolean,
    val coins: Int = 0,
    val extensionMinutes: Int = 30,
    val extensionCost: Int = 50,
) {
    /** Причина блокировки на текущий момент: limit, bedtime или null. */
    fun blockReasonAt(now: LocalTime = LocalTime.now()): String? {
        if (limitMinutes > 0 && usedMinutes >= limitMinutes) return "limit"
        if (isBedtime(now)) return "bedtime"
        return null
    }

    fun isBedtime(now: LocalTime = LocalTime.now()): Boolean {
        val from = parseTime(bedtimeStart) ?: return false
        val to = parseTime(bedtimeEnd) ?: return false
        return if (from <= to) now >= from && now < to else now >= from || now < to
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        val parts = value.split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    companion object {
        fun fromJson(raw: String): Policy {
            val data = JSONObject(raw)
            val policy = data.getJSONObject("policy")
            return Policy(
                childName = data.optJSONObject("child")?.optString("name").orEmpty(),
                limitMinutes = data.optInt("limitMinutes", 0),
                usedMinutes = data.optInt("usedMinutes", 0),
                remainMinutes = data.optInt("remainMinutes", 0),
                bedtimeStart = policy.optString("bedtime_start", "21:00"),
                bedtimeEnd = policy.optString("bedtime_end", "07:00"),
                webFilter = policy.optInt("web_filter", 0) == 1,
                coins = data.optInt("coins", 0),
                extensionMinutes = data.optJSONObject("extension")?.optInt("minutes", 30) ?: 30,
                extensionCost = data.optJSONObject("extension")?.optInt("costCoins", 50) ?: 50,
            )
        }
    }
}
