package app.familyos.guard.data

import org.json.JSONArray
import org.json.JSONObject

data class ParentAccount(
    val id: String,
    val title: String,
    val currency: String,
    val balance: Double,
    val type: String,
)

data class ParentCategory(
    val id: String,
    val title: String,
    val kind: String,
)

data class ParentTx(
    val id: String,
    val date: String,
    val amount: Double,
    val currency: String,
    val kind: String,
    val payee: String?,
    val categoryId: String?,
)

data class CategoryTotal(
    val title: String,
    val total: Double,
)

data class MoneyOverview(
    val currency: String,
    val accounts: List<ParentAccount>,
    val income: Double,
    val expense: Double,
    val byCategory: List<CategoryTotal>,
    val recent: List<ParentTx>,
) {
    companion object {
        fun fromJson(root: JSONObject): MoneyOverview {
            val data = root.getJSONObject("data")
            val accounts = data.optJSONArray("accounts") ?: JSONArray()
            val totals = data.optJSONArray("totals") ?: JSONArray()
            val categories = data.optJSONArray("byCategory") ?: JSONArray()
            val recent = data.optJSONArray("recent") ?: JSONArray()

            var income = 0.0
            var expense = 0.0
            for (i in 0 until totals.length()) {
                val t = totals.getJSONObject(i)
                income += t.optDouble("income", 0.0)
                expense += t.optDouble("expense", 0.0)
            }

            return MoneyOverview(
                currency = data.optString("currency", "RUB"),
                accounts = (0 until accounts.length()).map { i ->
                    val a = accounts.getJSONObject(i)
                    ParentAccount(
                        id = a.optString("id"),
                        title = a.optString("title"),
                        currency = a.optString("currency"),
                        balance = a.optDouble("balance", 0.0),
                        type = a.optString("type"),
                    )
                },
                income = income,
                expense = expense,
                byCategory = (0 until categories.length()).map { i ->
                    val c = categories.getJSONObject(i)
                    CategoryTotal(
                        title = c.optString("title", "Без категории"),
                        total = c.optDouble("total", 0.0),
                    )
                },
                recent = (0 until recent.length()).map { i ->
                    val t = recent.getJSONObject(i)
                    ParentTx(
                        id = t.optString("id"),
                        date = t.optString("occurred_at").take(10),
                        amount = t.optDouble("amount", 0.0),
                        currency = t.optString("currency"),
                        kind = t.optString("kind", "expense"),
                        payee = if (t.isNull("payee")) null else t.optString("payee"),
                        categoryId = if (t.isNull("category_id")) null else t.optString("category_id"),
                    )
                },
            )
        }
    }
}

data class ChildCard(
    val id: String,
    val name: String,
    val nickname: String?,
    val school: String?,
    val grade: String?,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val remainingMinutes: Int,
    val pendingRequests: Int,
    val avatarUrl: String?,
    val online: Boolean = false,
    val place: String? = null,
    val lastSeenAt: String? = null,
    val kcalTarget: Int = 2000,
    val appsCount: Int = 0,
    val bedtimeStart: String = "21:00",
    val bedtimeEnd: String = "07:00",
) {
    val nightModeOn: Boolean get() = bedtimeStart != bedtimeEnd

    val initial: String get() = (nickname ?: name).trim().take(1).uppercase().ifBlank { "?" }

    companion object {
        fun listFromOverview(root: JSONObject, publicUrl: String): List<ChildCard> {
            val data = root.optJSONObject("data") ?: return emptyList()
            val children = data.optJSONArray("children") ?: return emptyList()
            return (0 until children.length()).map { i ->
                val c = children.getJSONObject(i)
                val policy = c.optJSONObject("policy")
                val requests = c.optJSONArray("pendingRequests")
                ChildCard(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    nickname = if (c.isNull("nickname")) null else c.optString("nickname").ifBlank { null },
                    school = if (c.isNull("school")) null else c.optString("school").ifBlank { null },
                    grade = if (c.isNull("grade")) null else c.optString("grade").ifBlank { null },
                    usedMinutes = c.optInt("usedMinutes", 0),
                    limitMinutes = c.optInt("limitMinutes", policy?.optInt("screen_limit_min") ?: 120),
                    remainingMinutes = c.optInt("remainingMinutes", 0),
                    pendingRequests = requests?.length() ?: 0,
                    avatarUrl = if (c.isNull("avatar_path") || c.optString("avatar_path").isBlank()) null
                    else "${publicUrl.trimEnd('/')}/uploads/" + c.optString("avatar_path"),
                    online = c.optBoolean("online", false),
                    place = if (c.isNull("place")) null else c.optString("place").ifBlank { null },
                    lastSeenAt = if (c.isNull("lastSeenAt")) null else c.optString("lastSeenAt").ifBlank { null },
                    kcalTarget = c.optInt("kcalTarget", 2000),
                    appsCount = c.optInt("appsCount", 0),
                    bedtimeStart = c.optString("bedtimeStart", "21:00"),
                    bedtimeEnd = c.optString("bedtimeEnd", "07:00"),
                )
            }
        }
    }
}

data class PendingEvent(
    val id: String,
    val childId: String,
    val title: String,
    val description: String?,
    val date: String,
    val startTime: String,
    val durationMin: Int,
    val lat: Double?,
    val lng: Double?,
    val geofenceId: String?,
    val status: String,
) {
    companion object {
        fun listFrom(root: JSONObject): List<PendingEvent> {
            val array = root.optJSONArray("data") ?: return emptyList()
            return (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                PendingEvent(
                    id = e.optString("id"),
                    childId = e.optString("child_id"),
                    title = e.optString("title"),
                    description = if (e.isNull("description")) null else e.optString("description"),
                    date = e.optString("date"),
                    startTime = e.optString("start_time"),
                    durationMin = e.optInt("duration_min", 60),
                    lat = if (e.isNull("lat")) null else e.optDouble("lat"),
                    lng = if (e.isNull("lng")) null else e.optDouble("lng"),
                    geofenceId = if (e.isNull("geofence_id")) null else e.optString("geofence_id"),
                    status = e.optString("status"),
                )
            }
        }
    }
}
