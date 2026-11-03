package com.etrisad.zenith.data.website

import java.util.Locale

object WebsiteKeywordMatcher {

    data class Patterns(
        val regexes: List<Regex>,
        val literals: List<String>
    ) {
        val isEmpty: Boolean get() = regexes.isEmpty() && literals.isEmpty()
    }

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
