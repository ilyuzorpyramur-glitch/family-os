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
    val approval: String,
    val photosRequired: Int,
    val photos: List<String>,
) {
    companion object {
        fun fromJson(o: JSONObject): QuestItem = QuestItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = if (o.isNull("description")) null else o.optString("description").ifBlank { null },
            kind = o.optString("kind", "once"),
            xp = o.optInt("xp", 0),
            coins = o.optInt("coins", 0),
            stat = if (o.isNull("stat")) null else o.optString("stat").ifBlank { null },
            status = o.optString("status", "active"),
            approval = o.optString("approval", "parent"),
            photosRequired = o.optInt("photos_required", 0),
            photos = o.optJSONArray("photos")?.let { arr ->
                (0 until arr.length()).mapNotNull { index ->
                    arr.optJSONObject(index)?.optString("url")?.ifBlank { null }
                }
            }.orEmpty(),
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
    val photoUrl: String?,
) {
    companion object {
        fun fromJson(o: JSONObject): ShopItem = ShopItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = if (o.isNull("description")) null else o.optString("description").ifBlank { null },
            priceCoins = o.optInt("price_coins", 0),
            stock = if (o.isNull("stock")) null else o.optInt("stock"),
            photoUrl = if (o.isNull("photoUrl")) null else o.optString("photoUrl").ifBlank { null },
        )
    }
}

data class ProposalItem(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val priceCoins: Int?,
    val photoUrl: String?,
) {
    companion object {
        fun fromJson(o: JSONObject): ProposalItem = ProposalItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = if (o.isNull("description")) null else o.optString("description").ifBlank { null },
            status = o.optString("status", "pending"),
            priceCoins = if (o.isNull("price_coins")) null else o.optInt("price_coins"),
            photoUrl = if (o.isNull("photoUrl")) null else o.optString("photoUrl").ifBlank { null },
        )
    }
}

data class ShopData(
    val profile: Profile,
    val items: List<ShopItem>,
    val pending: List<PurchaseItem>,
    val proposals: List<ProposalItem>,
) {
    companion object {
        fun fromJson(root: JSONObject): ShopData {
            val data = root.getJSONObject("data")
            return ShopData(
                profile = Profile.fromJson(data.getJSONObject("profile")),
                items = data.optJSONArray("items").toShopItems(),
                pending = data.optJSONArray("pending").toPurchases(),
                proposals = data.optJSONArray("proposals")?.let { arr ->
                    (0 until arr.length()).map { ProposalItem.fromJson(arr.getJSONObject(it)) }
                }.orEmpty(),
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

data class GeofenceDto(
    val id: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val notifyEnter: Boolean,
    val notifyExit: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject): GeofenceDto = GeofenceDto(
            id = o.optString("id"),
            label = o.optString("label"),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            radiusM = o.optInt("radiusM", 150),
            notifyEnter = o.optBoolean("notifyEnter", true),
            notifyExit = o.optBoolean("notifyExit", true),
        )
    }
}
