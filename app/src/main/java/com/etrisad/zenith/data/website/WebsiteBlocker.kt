package com.etrisad.zenith.data.website

import com.etrisad.zenith.data.preferences.UserPreferences

class WebsiteBlocker {

    @Volatile private var enabled = false
    @Volatile private var blockAdult = true
    @Volatile private var userPatterns = WebsiteKeywordMatcher.Patterns(emptyList(), emptyList())

    private val adultPatterns =
        WebsiteKeywordMatcher.compile(AdultWebsitePreset.KEYWORDS)

    fun updatePreferences(preferences: UserPreferences) {
        enabled = preferences.websiteBlockerEnabled
        blockAdult = preferences.websiteBlockerBlockAdult
        userPatterns = WebsiteKeywordMatcher.compile(preferences.websiteBlockerKeywords)
    }

    fun blockReason(domain: String?, urlIdentifier: String?): String? {
        if (!enabled) return null
        val identifier = urlIdentifier?.takeIf { it.isNotBlank() } ?: domain ?: return null
        if (isInternalBrowserPage(identifier)) return null

        val candidates = if (domain != null && domain != identifier) {
            listOf(identifier, domain)
        } else {
            listOf(identifier)
        }

        if (blockAdult && candidates.any { WebsiteKeywordMatcher.matchesPatterns(adultPatterns, it) }) {
            return REASON_ADULT
        }
        if (candidates.any { WebsiteKeywordMatcher.matchesPatterns(userPatterns, it) }) {
            return REASON_KEYWORD
        }
        return null
    }

    fun isEnabled(): Boolean = enabled

    private fun isInternalBrowserPage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("chrome://") ||
            lower.startsWith("chrome-native://") ||
            lower.startsWith("about:") ||
            lower.startsWith("file://") ||
            lower.startsWith("data:") ||
            lower.startsWith("edge://") ||
            lower.startsWith("brave://") ||
            lower.startsWith("opera://") ||
            lower.startsWith("vivaldi://") ||
            lower.contains("newtab") ||
            lower.contains("new-tab") ||
            !lower.contains('.')
    }

    companion object {
        const val REASON_ADULT = "adult"
        const val REASON_KEYWORD = "keyword"
    }
}
