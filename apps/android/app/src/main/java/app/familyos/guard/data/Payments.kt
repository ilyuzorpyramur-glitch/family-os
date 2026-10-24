package app.familyos.guard.data

import org.json.JSONObject

/** Запрос на оплату по QR СБП. Деньги переводит банковское приложение. */
data class PaymentItem(
    val id: String,
    val amount: Double,
    val purpose: String?,
    val qrPayload: String,
    val status: String,
    val childName: String?,
    val createdAt: String,
) {
    val isPending: Boolean get() = status == "pending"

    companion object {
        fun fromJson(item: JSONObject) = PaymentItem(
            id = item.optString("id"),
            amount = if (item.isNull("amount")) 0.0 else item.optDouble("amount", 0.0),
            purpose = item.optString("purpose").takeIf { it.isNotBlank() && it != "null" },
            qrPayload = item.optString("qr_payload"),
            status = item.optString("status", "pending"),
            childName = item.optString("child_name").takeIf { it.isNotBlank() && it != "null" },
            createdAt = item.optString("created_at"),
        )

        fun listFrom(root: JSONObject?): List<PaymentItem> {
            val array = root?.optJSONArray("items") ?: return emptyList()
            return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }
    }
}

data class PayLimits(val perRequest: Int, val daily: Int, val spentToday: Double)

data class PaymentData(
    val limits: PayLimits,
    val items: List<PaymentItem>,
) {
    companion object {
        fun fromJson(root: JSONObject?): PaymentData {
            val limits = root?.optJSONObject("limits")
            return PaymentData(
                limits = PayLimits(
                    perRequest = limits?.optInt("perRequest", 1500) ?: 1500,
                    daily = limits?.optInt("daily", 3000) ?: 3000,
                    spentToday = limits?.optDouble("spentToday", 0.0) ?: 0.0,
                ),
                items = PaymentItem.listFrom(root),
            )
        }
    }
}

/** Разбор платёжного QR до отправки запроса. */
data class ParsedPayment(
    val isPayment: Boolean,
    val amount: Double?,
    val purpose: String?,
    val scheme: String,
) {
    companion object {
        fun fromJson(root: JSONObject?) = ParsedPayment(
            isPayment = root?.optBoolean("isPayment") ?: false,
            amount = root?.let { if (it.isNull("amount")) null else it.optDouble("amount") },
            purpose = root?.optString("purpose")?.takeIf { it.isNotBlank() && it != "null" },
            scheme = root?.optString("scheme") ?: "unknown",
        )
    }
}
