package app.familyos.guard.data

import org.json.JSONObject

data class GeofenceStatus(
    val id: String,
    val label: String,
    val inside: Boolean,
    val fineCoins: Int,
)

data class ShortTask(
    val id: String,
    val title: String,
    val xp: Int,
    val coins: Int,
    val status: String,
)

data class HomeData(
    val name: String,
    val nickname: String?,
    val school: String?,
    val grade: String?,
    val avatarUrl: String?,
    val level: Int,
    val xp: Int,
    val coins: Int,
    val xpForNextLevel: Int,
    val progress: Float,
    val streak: Int,
    val strength: Int,
    val intellect: Int,
    val creativity: Int,
    val kindness: Int,
    val discipline: Int,
    val activeTasks: Int,
    val pendingTasks: Int,
    val approvedToday: Int,
    val screenMinutes: Int,
    val geofences: List<GeofenceStatus>,
    val tasks: List<ShortTask>,
) {
    companion object {
        fun fromJson(root: JSONObject): HomeData {
            val data = root.getJSONObject("data")
            val child = data.getJSONObject("child")
            val profile = data.getJSONObject("profile")
            val today = data.getJSONObject("today")
            val fences = data.optJSONArray("geofences") ?: org.json.JSONArray()
            val tasks = data.optJSONArray("tasks") ?: org.json.JSONArray()
            return HomeData(
                name = child.optString("name"),
                nickname = if (child.isNull("nickname")) null else child.optString("nickname").ifBlank { null },
                school = if (child.isNull("school")) null else child.optString("school").ifBlank { null },
                grade = if (child.isNull("grade")) null else child.optString("grade").ifBlank { null },
                avatarUrl = if (child.isNull("avatarUrl")) null else child.optString("avatarUrl").ifBlank { null },
                level = profile.optInt("level", 1),
                xp = profile.optInt("xp", 0),
                coins = profile.optInt("coins", 0),
                xpForNextLevel = profile.optInt("xpForNextLevel", 50),
                progress = profile.optDouble("progress", 0.0).toFloat(),
                streak = profile.optInt("streak_days", 0),
                strength = profile.optInt("strength", 0),
                intellect = profile.optInt("intellect", 0),
                creativity = profile.optInt("creativity", 0),
                kindness = profile.optInt("kindness", 0),
                discipline = profile.optInt("discipline", 0),
                activeTasks = today.optInt("active", 0),
                pendingTasks = today.optInt("pending", 0),
                approvedToday = today.optInt("approved", 0),
                screenMinutes = data.optInt("screenMinutes", 0),
                geofences = (0 until fences.length()).map { index ->
                    val f = fences.getJSONObject(index)
                    GeofenceStatus(
                        id = f.optString("id"),
                        label = f.optString("label"),
                        inside = f.optBoolean("inside", false),
                        fineCoins = f.optInt("fineCoins", 0),
                    )
                },
                tasks = (0 until tasks.length()).map { index ->
                    val q = tasks.getJSONObject(index)
                    ShortTask(
                        id = q.optString("id"),
                        title = q.optString("title"),
                        xp = q.optInt("xp", 0),
                        coins = q.optInt("coins", 0),
                        status = q.optString("status"),
                    )
                },
            )
        }
    }
}

data class ProfileData(
    val name: String,
    val nickname: String?,
    val school: String?,
    val grade: String?,
    val birthDate: String?,
    val avatarUrl: String?,
    val level: Int,
    val xp: Int,
    val coins: Int,
    val xpForNextLevel: Int,
    val progress: Float,
    val strength: Int,
    val intellect: Int,
    val creativity: Int,
    val kindness: Int,
    val discipline: Int,
) {
    companion object {
        fun fromJson(root: JSONObject): ProfileData {
            val data = root.getJSONObject("data")
            val child = data.getJSONObject("child")
            val profile = data.getJSONObject("profile")
            return ProfileData(
                name = child.optString("name"),
                nickname = if (child.isNull("nickname")) null else child.optString("nickname").ifBlank { null },
                school = if (child.isNull("school")) null else child.optString("school").ifBlank { null },
                grade = if (child.isNull("grade")) null else child.optString("grade").ifBlank { null },
                birthDate = if (child.isNull("birthDate")) null else child.optString("birthDate"),
                avatarUrl = if (child.isNull("avatarUrl")) null else child.optString("avatarUrl").ifBlank { null },
                level = profile.optInt("level", 1),
                xp = profile.optInt("xp", 0),
                coins = profile.optInt("coins", 0),
                xpForNextLevel = profile.optInt("xpForNextLevel", 50),
                progress = profile.optDouble("progress", 0.0).toFloat(),
                strength = profile.optInt("strength", 0),
                intellect = profile.optInt("intellect", 0),
                creativity = profile.optInt("creativity", 0),
                kindness = profile.optInt("kindness", 0),
                discipline = profile.optInt("discipline", 0),
            )
        }
    }
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: String,
    val durationMin: Int,
    val status: String,
)

data class CalendarDay(
    val date: String,
    val questsDone: Int,
    val questsPlanned: Int,
    val events: List<CalendarEvent>,
)

data class CalendarData(
    val month: String,
    val days: List<CalendarDay>,
) {
    companion object {
        fun fromJson(root: JSONObject): CalendarData {
            val data = root.getJSONObject("data")
            val days = data.optJSONArray("days") ?: org.json.JSONArray()
            return CalendarData(
                month = data.optString("month"),
                days = (0 until days.length()).map { i ->
                    val d = days.getJSONObject(i)
                    val events = d.optJSONArray("events") ?: org.json.JSONArray()
                    CalendarDay(
                        date = d.optString("date"),
                        questsDone = d.optInt("questsDone", 0),
                        questsPlanned = d.optInt("questsPlanned", 0),
                        events = (0 until events.length()).map { j ->
                            val e = events.getJSONObject(j)
                            CalendarEvent(
                                id = e.optString("id"),
                                title = e.optString("title"),
                                startTime = e.optString("startTime"),
                                durationMin = e.optInt("durationMin", 60),
                                status = e.optString("status"),
                            )
                        },
                    )
                },
            )
        }
    }
}
