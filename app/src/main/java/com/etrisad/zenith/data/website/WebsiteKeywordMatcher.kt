/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, literal matching accepts subdomains, and
 * the compiled pattern set is a small data class instead of a Pair.
 */
package com.etrisad.zenith.data.website

import java.util.Locale

/**
 * Pure URL/keyword matching shared by the website blocker and its settings UI.
 *
 * A compiled pattern set is a list of raw/wildcard regexes plus lowercase literal
 * keywords. Patterns are matched against a domain+path identifier such as
 * `youtube.com/shorts`.
 */
object WebsiteKeywordMatcher {

    data class Patterns(
        val regexes: List<Regex>,
        val literals: List<String>
    ) {
        val isEmpty: Boolean get() = regexes.isEmpty() && literals.isEmpty()
    }

    /**
     * Compiles a collection of keyword patterns.
     *
     * Pattern types:
     *   r:<expr>   - raw regex (e.g. r:(?:shorts|reels))
     *   * / ?      - glob wildcard (* = any chars, ? = one char)
     *   otherwise  - URL-aware literal (domain, path, or plain word)
     */
    fun compile(keywords: Collection<String>): Patterns {
        val regexes = ArrayList<Regex>(keywords.size)
        val literals = ArrayList<String>(keywords.size)
        for (input in keywords) {
            val keyword = input.trim()
            if (keyword.isEmpty()) continue

            when {
                keyword.startsWith("r:", ignoreCase = true) ->
                    runCatching { Regex(keyword.substring(2), RegexOption.IGNORE_CASE) }
                        .getOrNull()
                        ?.let { regexes.add(it) }
                keyword.contains('*') || keyword.contains('?') ->
                    regexes.add(wildcardToRegex(normalize(keyword)))
                else -> literals.add(normalize(keyword))
            }
        }
        return Patterns(regexes, literals)
    }

    private fun wildcardToRegex(pattern: String): Regex {
        val optionalSubdomain = pattern.startsWith("*.")
        val glob = if (optionalSubdomain) pattern.removePrefix("*.") else pattern
        val escaped = buildString {
            glob.forEach { character ->
                append(
                    when (character) {
                        '*' -> ".*"
                        '?' -> "."
                        else -> Regex.escape(character.toString())
                    }
                )
            }
        }
        val prefix = when {
            // A leading "*." means "this domain or any subdomain of it".
            optionalSubdomain -> "^(?:[^/]+\\.)*"
            pattern.startsWith("/") -> "^[^/]+"
            pattern.substringBefore('/').contains('.') -> "^"
            else -> ""
        }
        val suffix = if (optionalSubdomain && !pattern.contains('/')) "(?=$|[/?#])" else ""
        return Regex(prefix + escaped + suffix, RegexOption.IGNORE_CASE)
    }

    fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')

    /**
     * URL-aware literal match. [keyword] must already be lowercase.
     *
     * Handles:
     *   - Exact domain match:   "youtube.com"  -> "youtube.com"
     *   - Domain prefix:        "youtube.com"  -> "youtube.com/shorts"
     *   - Subdomains:           "youtube.com"  -> "m.youtube.com/shorts"
     *   - Path segment:         "/shorts"      -> "youtube.com/shorts"
     *   - Domain word:          "youtube"      -> "youtube.com", "m.youtube.com"
     */
    private fun matchesLiteral(keyword: String, urlIdentifier: String): Boolean {
        val url = normalize(urlIdentifier)

        if (keyword.startsWith("/")) {
            val pathStart = url.indexOf('/')
            if (pathStart < 0) return false
            val path = url.substring(pathStart)
            return path == keyword || path.startsWith("$keyword/") ||
                path.startsWith("$keyword?") || path.startsWith("$keyword#")
        }

        val keywordPathStart = keyword.indexOf('/')
        if (keywordPathStart > 0) {
            val keywordDomain = keyword.substring(0, keywordPathStart)
            val keywordPath = keyword.substring(keywordPathStart)
            val urlDomain = url.substringBefore('/')
            if (urlDomain != keywordDomain && !urlDomain.endsWith(".$keywordDomain")) {
                return false
            }
            val pathAndQuery = url.substring(urlDomain.length)
            return pathAndQuery == keywordPath || pathAndQuery.startsWith("$keywordPath/") ||
                pathAndQuery.startsWith("$keywordPath?") || pathAndQuery.startsWith("$keywordPath#")
        }

        if (url == keyword || url.startsWith("$keyword/") ||
            url.startsWith("$keyword?") || url.startsWith("$keyword#")
        ) {
            return true
        }

        val domain = url.substringBefore('/').substringBefore('?').substringBefore('#')
        if (keyword.contains('.')) {
            // A bare domain keyword also covers every subdomain of it.
            return domain.endsWith(".$keyword")
        }
        if (!keyword.contains('/')) {
            if (domain.split('.').any { it == keyword }) return true
        }

        return false
    }

    fun matchesPatterns(patterns: Patterns, urlIdentifier: String): Boolean {
        if (patterns.isEmpty) return false
        val normalizedUrl = normalize(urlIdentifier)
        return patterns.regexes.any { it.containsMatchIn(normalizedUrl) } ||
            patterns.literals.any { matchesLiteral(it, normalizedUrl) }
    }
}
