package com.etrisad.zenith.data.website

object TldSuggestions {
    private val COMMON_TLDS = listOf(
        ".com", ".net", ".org",
        ".id", ".co.id", ".go.id", ".my.id", ".web.id", ".or.id",
        ".ac.id", ".sch.id", ".desa.id", ".biz.id",
        ".xyz", ".io", ".app", ".dev", ".me",
        ".info", ".online", ".site", ".tech", ".store",
        ".blog", ".cloud", ".xd", ".ai",
        ".tv", ".cc", ".ws", ".pro", ".link", ".live", ".today",
        ".com.au", ".com.sg", ".com.my",
        ".co", ".us", ".uk", ".ca", ".de", ".jp", ".fr", ".eu",
        ".edu", ".gov", ".mil",
        ".art", ".design", ".digital", "email",
        ".finance", ".health", ".legal", ".media", ".money",
        ".news", ".photos", ".press", ".school", ".social",
        ".software", ".solutions", ".support", ".video", ".world"
    )

    fun suggest(input: String): List<String> {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val isFullUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val base = if (isFullUrl) {
            try {
                val withoutProtocol = trimmed.substringAfter("://")
                withoutProtocol.split("/").firstOrNull()?.removePrefix("www.") ?: trimmed
            } catch (_: Exception) { trimmed }
        } else {
            trimmed.removePrefix("www.")
        }

        if (base.contains(".")) {
            return listOf(normalizeToUrl(trimmed))
        }

        return COMMON_TLDS.map { "$base$it" }
    }

    private fun normalizeToUrl(input: String): String {
        val trimmed = input.trim()
        val hasProtocol = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        return if (hasProtocol) trimmed else "https://$trimmed"
    }
}
