package app.familyos.guard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальный кэш и очередь действий: приложение показывает данные без сети
 * и досылает отложенные действия, когда сервер снова доступен.
 */
class OfflineStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("familyos_offline", Context.MODE_PRIVATE)

    fun cache(key: String, json: String) {
        prefs.edit()
            .putString("cache_$key", json)
            .putLong("cache_at_$key", System.currentTimeMillis())
            .apply()
    }

    fun cached(key: String): String? = prefs.getString("cache_$key", null)

    fun cachedAt(key: String): Long = prefs.getLong("cache_at_$key", 0L)

    fun cacheFresh(key: String, maxAgeMs: Long = 5 * 60_000L): Boolean {
        val at = cachedAt(key)
        return at > 0 && System.currentTimeMillis() - at < maxAgeMs
    }

    fun enqueue(kind: String, path: String, body: JSONObject?, method: String = "POST") {
        val items = JSONArray(pendingRaw())
        items.put(
            JSONObject()
                .put("kind", kind)
                .put("path", path)
                .put("method", method)
                .put("body", body?.toString() ?: "")
                .put("at", System.currentTimeMillis()),
        )
        prefs.edit().putString(KEY_OUTBOX, items.toString()).apply()
    }

    fun pendingRaw(): String = prefs.getString(KEY_OUTBOX, "[]") ?: "[]"

    fun pending(): List<JSONObject> {
        val array = runCatching { JSONArray(pendingRaw()) }.getOrElse { JSONArray() }
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    fun replacePending(items: List<JSONObject>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs.edit().putString(KEY_OUTBOX, array.toString()).apply()
    }

    fun remove(item: JSONObject) {
        val rest = pending().filter { it.optLong("at") != item.optLong("at") }
        replacePending(rest)
    }

    companion object {
        private const val KEY_OUTBOX = "outbox"
    }
}
