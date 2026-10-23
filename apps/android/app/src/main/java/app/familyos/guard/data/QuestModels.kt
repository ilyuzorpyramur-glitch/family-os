package app.familyos.guard.data

import org.json.JSONArray
import org.json.JSONObject

data class Profile(
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
        fun fromJson(o: JSONObject): Profile = Profile(
            level = o.optInt("level", 1),
            xp = o.optInt("xp", 0),
            coins = o.optInt("coins", 0),
            xpForNextLevel = o.optInt("xpForNextLevel", 50),
            progress = o.optDouble("progress", 0.0).toFloat(),
            strength = o.optInt("strength", 0),
            intellect = o.optInt("intellect", 0),
            creativity = o.optInt("creativity", 0),
            kindness = o.optInt("kindness", 0),
            discipline = o.optInt("discipline", 0),
        )
    }
}

data class QuestItem(
    val id: String,
    val title: String,
    val description: String?,
    val kind: String,
    val xp: Int,
    val coins: Int,
    val stat: String?,
    val status: String,
) {
    companion object {
        fun fromJson(o: JSONObject): QuestItem = QuestItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.optString("description").ifBlank { null },
            kind = o.optString("kind", "once"),
            xp = o.optInt("xp", 0),
            coins = o.optInt("coins", 0),
            stat = o.optString("stat").ifBlank { null },
            status = o.optString("status", "active"),
        )
    }
}

data class PurchaseItem(
    val id: String,
    val title: String,
    val priceCoins: Int,
    val status: String,
) {
    companion object {
        fun fromJson(o: JSONObject): PurchaseItem = PurchaseItem(
            id = o.optString("id"),
            title = o.optString("title").ifBlank { "Покупка" },
            priceCoins = o.optInt("price_coins", 0),
            status = o.optString("status", "pending"),
        )
    }
}

data class QuestData(
    val profile: Profile,
    val active: List<QuestItem>,
    val completed: List<QuestItem>,
    val purchases: List<PurchaseItem>,
) {
    companion object {
        fun fromJson(root: JSONObject): QuestData {
            val data = root.getJSONObject("data")
            return QuestData(
                profile = Profile.fromJson(data.getJSONObject("profile")),
                active = data.optJSONArray("active").toQuests(),
                completed = data.optJSONArray("completed").toQuests(),
                purchases = data.optJSONArray("purchases").toPurchases(),
            )
        }
    }
}

data class ShopItem(
    val id: String,
    val title: String,
    val description: String?,
    val priceCoins: Int,
    val stock: Int?,
) {
    companion object {
        fun fromJson(o: JSONObject): ShopItem = ShopItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.optString("description").ifBlank { null },
            priceCoins = o.optInt("price_coins", 0),
            stock = if (o.isNull("stock")) null else o.optInt("stock"),
        )
    }
}

data class ShopData(
    val profile: Profile,
    val items: List<ShopItem>,
    val pending: List<PurchaseItem>,
) {
    companion object {
        fun fromJson(root: JSONObject): ShopData {
            val data = root.getJSONObject("data")
            return ShopData(
                profile = Profile.fromJson(data.getJSONObject("profile")),
                items = data.optJSONArray("items").toShopItems(),
                pending = data.optJSONArray("pending").toPurchases(),
            )
        }
    }
}

private fun JSONArray?.toQuests(): List<QuestItem> {
    if (this == null) return emptyList()
    return (0 until length()).map { QuestItem.fromJson(getJSONObject(it)) }
}

private fun JSONArray?.toPurchases(): List<PurchaseItem> {
    if (this == null) return emptyList()
    return (0 until length()).map { PurchaseItem.fromJson(getJSONObject(it)) }
}

private fun JSONArray?.toShopItems(): List<ShopItem> {
    if (this == null) return emptyList()
    return (0 until length()).map { ShopItem.fromJson(getJSONObject(it)) }
}
