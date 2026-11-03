package com.etrisad.zenith.data.website

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import java.net.URI
import java.util.regex.Pattern

object WebsiteRepository {

    const val WEBSITE_PREFIX = "zenith-web:"

    fun isWebsitePackageName(packageName: String): Boolean {
        return packageName.startsWith(WEBSITE_PREFIX)
    }

    fun extractDomainFromPackageName(packageName: String): String {
        return packageName.removePrefix(WEBSITE_PREFIX)
    }

    fun createPackageName(domain: String): String {
        return "$WEBSITE_PREFIX$domain"
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val hasProtocol = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        return if (hasProtocol) trimmed else "https://$trimmed"
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            (uri.host ?: url).removePrefix("www.")
        } catch (_: Exception) {
            url.removePrefix("www.").removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: url
        }
    }

    data class SiteIdentifier(val domain: String, val urlIdentifier: String)

    fun extractSiteIdentifier(urlText: String): SiteIdentifier {
        return try {
            var url = urlText
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val uri = URI(url)
            val domain = (uri.host ?: urlText).lowercase().removePrefix("www.")

            val firstSegment = uri.path
                ?.split('/')
                ?.firstOrNull { it.isNotEmpty() }
                ?.let { "/$it" }
                .orEmpty()
            val identifier = if (firstSegment.isEmpty()) domain else "$domain$firstSegment"

            SiteIdentifier(domain, identifier)
        } catch (e: Exception) {
            SiteIdentifier(urlText, urlText)
        }
    }

    fun filterUrlFromText(inputText: String?): String? {
        if (inputText.isNullOrBlank()) return null
        val urlRegex = """(?:https?://|www\.)?[a-zA-Z0-9][a-zA-Z0-9\-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}(?:[/\?#][a-zA-Z0-9\-._~:/?#\[\]@!${'$'}&'()*+,;=%]*)?"""
        val pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(inputText)
        if (matcher.find()) {
            return matcher.group(0)?.trimEnd('.', ',', ')', ']', '\'', '"', '>')
        }
        return null
    }

    fun getDisplayName(domain: String, url: String): String {
        val name = domain.removePrefix("www.").split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: domain
        return name
    }

    fun getFaviconUrl(domain: String): String {
        return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    fun validateUrl(input: String): String? {
        val normalized = normalizeUrl(input)
        return try {
            val uri = URI(normalized)
            if (uri.host != null && uri.host.contains(".")) normalized else null
        } catch (_: Exception) {
            null
        }
    }

    fun createLaunchIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val KNOWN_BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.chromium.chrome",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.duckduckgo.mobile.android",
        "com.android.browser",
        "com.google.android.apps.giant",
        "mark.via.gp",
        "com.mmbox.browser",
        "com.mi.globalbrowser",
        "com.android.chrome.beta",
        "com.uc.browser.en",
        "com.uc.browser.id",
        "com.opera.browser.beta",
        "com.sec.android.app.sbrowser.beta",
        "com.microsoft.emmx.beta",
        "com.microsoft.edge.canary",
        "com.microsoft.edge.beta",
        "org.cromite.cromite",
        "app.vanadium.browser",
        "org.mozilla.fennec_fdroid",
        "org.ironfoxoss.ironfox",
        "io.github.forkmaintainers.iceraven",
        "com.jqssun.titanium",
        "io.github.jqssun.titanium",
        "com.jqssun.android.titanium",
        "jqssun.titanium"
    )

    private val browserCheckCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isKnownBrowser(packageName: String): Boolean {
        if (packageName in KNOWN_BROWSERS) return true
        val lower = packageName.lowercase()
        return lower.contains("browser") ||
            lower.contains("chrome") ||
            lower.contains("firefox") ||
            lower.contains("opera") ||
            lower.contains("edge") ||
            lower.contains("brave") ||
            lower.contains("vivaldi") ||
            lower.contains("duckduckgo") ||
            lower.contains("titanium") ||
            lower.contains("jqssun") ||
            lower.contains("cromite") ||
            lower.contains("vanadium") ||
            lower.contains("kiwi") ||
            lower.contains("via") ||
            lower.contains("uc") ||
            lower.contains("fenix") ||
            lower.contains("fennec")
    }

    fun isKnownBrowser(context: Context, packageName: String): Boolean {
        if (isKnownBrowser(packageName)) return true
        return browserCheckCache.getOrPut(packageName) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    setPackage(packageName)
                }
                val activities = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                activities.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isUrlBlocked(url: String, blockedKeywords: List<String>): Boolean {
        val normalizedUrl = normalizeUrl(url)
        for (keyword in blockedKeywords) {
            if (normalizedUrl.contains(keyword, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun isValidUrl(text: String): Boolean {
        return Patterns.WEB_URL.matcher(text.trim()).matches()
    }

    fun redirectBrowserToBlankPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("about:blank")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
