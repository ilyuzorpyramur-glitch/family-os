package app.familyos.guard.data

import org.json.JSONArray
import org.json.JSONObject

data class RecipeItem(
    val title: String,
    val quantity: Double,
    val unit: String,
    val available: Double,
    val enough: Boolean,
)

data class Recipe(
    val id: String,
    val title: String,
    val description: String?,
    val servings: Int,
    val minutes: Int,
    val steps: List<String>,
    val items: List<RecipeItem>,
    val canCook: Boolean,
    val missingCount: Int,
) {
    companion object {
        fun listFrom(root: JSONObject): List<Recipe> {
            val array = root.optJSONArray("data") ?: return emptyList()
            return (0 until array.length()).map { index -> fromJson(array.getJSONObject(index)) }
        }

        fun fromJson(o: JSONObject): Recipe {
            val items = o.optJSONArray("items") ?: JSONArray()
            val steps = o.optJSONArray("stepsList") ?: JSONArray()
            return Recipe(
                id = o.optString("id"),
                title = o.optString("title"),
                description = if (o.isNull("description")) null else o.optString("description"),
                servings = o.optInt("servings", 2),
                minutes = o.optInt("minutes", 30),
                steps = (0 until steps.length()).map { steps.optString(it) },
                items = (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    RecipeItem(
                        title = item.optString("title"),
                        quantity = item.optDouble("quantity", 0.0),
                        unit = item.optString("unit"),
                        available = item.optDouble("available", 0.0),
                        enough = item.optBoolean("enough", false),
                    )
                },
                canCook = o.optBoolean("canCook", false),
                missingCount = o.optInt("missingCount", 0),
            )
        }
    }
}

data class Product(
    val id: String,
    val title: String,
    val unit: String,
    val quantity: Double,
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    val imageUrl: String?,
    val barcode: String?,
) {
    companion object {
        fun listFrom(root: JSONObject): List<Product> {
            val array = root.optJSONArray("data") ?: return emptyList()
            return (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        }

        fun fromJson(o: JSONObject): Product = Product(
            id = o.optString("id"),
            title = o.optString("title"),
            unit = o.optString("unit", "шт"),
            quantity = o.optDouble("quantity", 0.0),
            kcal = if (o.isNull("kcal")) null else o.optDouble("kcal"),
            protein = if (o.isNull("protein")) null else o.optDouble("protein"),
            fat = if (o.isNull("fat")) null else o.optDouble("fat"),
            carbs = if (o.isNull("carbs")) null else o.optDouble("carbs"),
            imageUrl = if (o.isNull("image_url")) null else o.optString("image_url"),
            barcode = if (o.isNull("barcode")) null else o.optString("barcode"),
        )
    }
}
