package app.familyos.guard.data

import org.json.JSONObject

/** Калории за день: одна модель для обоих приложений. */
data class Nutrition(
    val date: String,
    val consumed: Int,
    val target: Int,
    val percent: Int,
    val meals: List<Meal>,
) {
    data class Meal(val id: String, val title: String, val kcal: Int)

    val left: Int get() = (target - consumed).coerceAtLeast(0)
    val over: Boolean get() = target > 0 && consumed > target

    companion object {
        fun fromJson(root: JSONObject?): Nutrition? {
            if (root == null) return null
            val array = root.optJSONArray("meals") ?: org.json.JSONArray()
            return Nutrition(
                date = root.optString("date"),
                consumed = root.optInt("consumed"),
                target = root.optInt("target", 2000),
                percent = root.optInt("percent"),
                meals = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    Meal(item.optString("id"), item.optString("title"), item.optInt("kcal"))
                },
            )
        }
    }
}
