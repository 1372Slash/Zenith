/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: no database/overlay dependencies; counting is reported
 * through a callback so the existing Reels Shield counters own persistence.
 */
package com.etrisad.zenith.service.reels

import android.os.SystemClock
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts short videos from the comparators produced by [ReelScriptRunner].
 *
 * Apps with a known pager are driven by pager positions; the others use a substantial
 * text-change comparator. Deduplication keeps a reel from being counted again when its
 * content scrolls back into view.
 */
class ReelCountTracker(
    private val onReelCounted: (String) -> Unit
) {

    companion object {
        /** Delay after the last pager scroll before the service asks for one more settled read. */
        const val PAGER_SETTLE_RECHECK_MS = 250L

        private const val UNKNOWN_INDEX = -1
        // Scroll events repeat every 100 ms while the pager moves, so this gap means it has stopped.
        private const val PAGER_SETTLE_MS = 150L
        private const val PAGED_REEL_HISTORY = 50
        private const val MAX_SKIPPED_REELS = 10
        private const val PROGRESSION_CACHE_SIZE = 50
    }

    private class PagerPosition(val index: Int, val scrolledAt: Long)
    private class PagedReel(
        val sessionStartedAt: Long,
        val index: Int,
        val author: String,
        var text: String
    )
    private class PagedSession(val startedAt: Long) {
        var current: PagedReel? = null
        val seenIndices = HashSet<Int>()
    }

    private val lastDynamicText = HashMap<String, String>(8)
    private val seenReelsCache = HashMap<String, LruCache<String, Boolean>>(8)

    // Written from onAccessibilityEvent and read by the background worker.
    private val pagerPositions = ConcurrentHashMap<String, PagerPosition>()
    private val pagedSessions = HashMap<String, PagedSession>(8)
    private val recentPagedReels = HashMap<String, ArrayDeque<PagedReel>>(8)

    fun isScriptSupported(packageName: String): Boolean =
        ReelAppConfig.dataFor(packageName) != null

    /**
     * Records the reel pager position. Cheap enough to run inline, so the position survives even
     * when the conflated background queue drops the scroll event itself.
     * Returns true for reel pager scrolls, which need a settled read once the pager stops.
     */
    fun onPagerEvent(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return false
        val pkg = event.packageName?.toString() ?: return false
        val pagerClassName = ReelAppConfig.dataFor(pkg)?.pagerClassName ?: return false
        // Horizontal pagers, such as the main tab pager, share the class but never scroll vertically.
        if (event.className?.toString() != pagerClassName || event.scrollDeltaY == 0) return false
        if (event.fromIndex < 0) return false
        pagerPositions[pkg] = PagerPosition(event.fromIndex, SystemClock.uptimeMillis())
        return true
    }

    fun onEvent(event: AccessibilityEvent, dynamicComparator: String?) {
        val pkg = event.packageName?.toString() ?: return
        val data = ReelAppConfig.dataFor(pkg) ?: return

        if (dynamicComparator == null) {
            lastDynamicText.remove(pkg)
            pagedSessions.remove(pkg)
            recentPagedReels.remove(pkg)
            return
        }
        if ((event.eventType and data.eventType) == 0) return

        if (data.pagerClassName != null) {
            checkForPagedReelProgression(pkg, dynamicComparator)
        } else {
            checkForReelProgression(
                pkg,
                dynamicComparator,
                data.deduplicateComparators,
                data.initialComparator
            )
        }
    }

    /** Drops the session when the app is left, so the next visit starts clean. */
    fun onLeftApp(packageName: String) {
        lastDynamicText.remove(packageName)
        pagedSessions.remove(packageName)
        recentPagedReels.remove(packageName)
        pagerPositions.remove(packageName)
    }

    /** Forgets every app, used when the feature is switched off or the service restarts. */
    fun resetAll() {
        lastDynamicText.clear()
        pagedSessions.clear()
        recentPagedReels.clear()
        pagerPositions.clear()
        seenReelsCache.clear()
    }

    private fun checkForReelProgression(
        pkg: String,
        currentText: String,
        deduplicateComparators: Boolean,
        initialComparator: String?
    ) {
        if (currentText.trim().isBlank()) {
            if (!initialComparator.isNullOrBlank() && lastDynamicText[pkg].isNullOrEmpty()) {
                lastDynamicText[pkg] = initialComparator
            }
            return
        }

        val previousText = lastDynamicText[pkg] ?: ""
        if (currentText != previousText) {
            val isSubstantialChange = isSubstantialTextChange(currentText, previousText)

            if (previousText.isNotEmpty() && isSubstantialChange) {
                if (!deduplicateComparators) {
                    onReelCounted(pkg)
                } else {
                    val appCache = seenReelsCache.getOrPut(pkg) {
                        LruCache(PROGRESSION_CACHE_SIZE)
                    }
                    if (appCache.get(currentText) == null) {
                        onReelCounted(pkg)
                        appCache.put(currentText, true)
                    }
                }
            }

            if (isSubstantialChange || currentText.length > previousText.length) {
                lastDynamicText[pkg] = currentText
            }
        }
    }

    /**
     * Counts reels in apps whose pager reports positions. A new pager position is always a new
     * reel. Without a position from this viewer session, a clear change in the reel text decides.
     * The first reel of a session counts too, unless it was just seen, such as after a tab switch.
     */
    private fun checkForPagedReelProgression(pkg: String, currentText: String) {
        val now = SystemClock.uptimeMillis()
        val session = pagedSessions.getOrPut(pkg) { PagedSession(startedAt = now) }
        val position = pagerPositions[pkg]
        // While the pager moves, its position lags behind and reads can mix two pages.
        if (position != null && now - position.scrolledAt < PAGER_SETTLE_MS) return
        if (currentText.isBlank()) return
        val author = currentText.substringBefore('\n').trim()
        if (author.isEmpty()) return

        val index = position?.takeIf { it.scrolledAt >= session.startedAt }?.index ?: UNKNOWN_INDEX
        val reel = PagedReel(session.startedAt, index, author, currentText)
        val previous = session.current
        if (previous != null && !isDifferentReel(previous, reel)) {
            previous.text = currentText
            return
        }

        session.current = reel
        // A quick swipe may have counted this page already while passing it.
        val countedWhilePassing = index != UNKNOWN_INDEX && index in session.seenIndices
        if (previous != null && previous.index != UNKNOWN_INDEX && index != UNKNOWN_INDEX) {
            countPassedReels(pkg, session, previous.index, index)
        }
        if (index != UNKNOWN_INDEX) session.seenIndices.add(index)

        val recent = recentPagedReels.getOrPut(pkg) { ArrayDeque() }
        val seenRecently = recent.any { !isDifferentReel(it, reel) }
        if (!seenRecently) {
            recent.addLast(reel)
            if (recent.size > PAGED_REEL_HISTORY) recent.removeFirst()
        }
        if (seenRecently || countedWhilePassing) return
        onReelCounted(pkg)
    }

    /** A quick swipe can pass a reel before it settles, but the pager still showed it. */
    private fun countPassedReels(pkg: String, session: PagedSession, from: Int, to: Int) {
        val passed = if (to > from) from + 1 until to else to + 1 until from
        for (passedIndex in passed.take(MAX_SKIPPED_REELS)) {
            if (session.seenIndices.add(passedIndex)) {
                onReelCounted(pkg)
            }
        }
    }

    private fun isDifferentReel(first: PagedReel, second: PagedReel): Boolean {
        // Positions restart in every pager, so they only compare within one session.
        if (first.sessionStartedAt == second.sessionStartedAt &&
            first.index != UNKNOWN_INDEX && second.index != UNKNOWN_INDEX
        ) {
            return first.index != second.index || first.author != second.author
        }
        return first.author != second.author || isSubstantialTextChange(first.text, second.text)
    }

    private fun isSubstantialTextChange(currentText: String, previousText: String): Boolean {
        if (currentText.isEmpty() || previousText.isEmpty()) return true

        fun countWords(text: String, wordCounts: HashMap<String, Int>) {
            val len = text.length
            var start = -1
            for (i in 0 until len) {
                if (text[i].isWhitespace()) {
                    if (start != -1) {
                        val word = text.substring(start, i)
                        wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
                        start = -1
                    }
                } else {
                    if (start == -1) start = i
                }
            }
            if (start != -1) {
                val word = text.substring(start, len)
                wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
            }
        }

        val currentWords = HashMap<String, Int>()
        val previousWords = HashMap<String, Int>()

        countWords(currentText, currentWords)
        countWords(previousText, previousWords)

        if (currentWords.isEmpty() || previousWords.isEmpty()) return true

        var intersectionSize = 0
        var totalSmaller = 0

        val smallerMap = if (currentWords.size < previousWords.size) currentWords else previousWords
        val largerMap = if (currentWords.size < previousWords.size) previousWords else currentWords

        for ((word, count) in smallerMap) {
            totalSmaller += count
            val largerCount = largerMap[word] ?: 0
            intersectionSize += minOf(count, largerCount)
        }

        if (totalSmaller == 0) return true

        val overlapRatio = intersectionSize.toFloat() / totalSmaller
        return overlapRatio < 0.90f
    }
}
