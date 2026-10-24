package app.familyos.guard.api

/** Разбор QR: familyos://pair?u=<url>&c=<code> или просто код. */
object PairPayload {
    data class Parsed(val url: String?, val code: String)

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        val query = if (text.contains("?")) text.substringAfter("?") else ""
        val params = HashMap<String, String>()
        query.split("&").forEach { chunk ->
            val key = chunk.substringBefore("=", "")
            val value = chunk.substringAfter("=", "")
            if (key.isNotBlank()) params[key] = java.net.URLDecoder.decode(value, "UTF-8")
        }
        val code = (params["c"] ?: params["code"] ?: "").uppercase()
        val url = (params["u"] ?: params["url"] ?: "").trim().trimEnd('/')
        if (code.isNotBlank()) return Parsed(url.ifBlank { null }, code)

        val plain = text.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return Parsed(null, plain)
    }
}
