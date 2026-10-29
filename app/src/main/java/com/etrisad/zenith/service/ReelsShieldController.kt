package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.data.preferences.ReelsShieldDefaults
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.overlay.ReelsShieldOverlayManager
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
class ReelsShieldController(
    private val service: ZenithService,
    private val preferencesRepository: UserPreferencesRepository,
    private val overlay: ReelsShieldOverlayManager
) {

    private val context: Context get() = service

    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val screenHeightPx = context.resources.displayMetrics.heightPixels
    private val screenWidthPx = context.resources.displayMetrics.widthPixels
    private val minSurfaceHeightPx = (screenHeightPx * MIN_SURFACE_HEIGHT_FRACTION).toInt()
    private val minSurfaceWidthPx = (screenWidthPx * MIN_SURFACE_WIDTH_FRACTION).toInt()
    private val scrollJumpPx = (screenHeightPx * SCROLL_JUMP_FRACTION).toInt()
    private val largeScrollSourcePx = (screenHeightPx * LARGE_SOURCE_FRACTION).toInt()
    private val sheetPaneMinHeightPx = (screenHeightPx * SHEET_PANE_HEIGHT_FRACTION).toInt()
    private val swipeDistancePx = (screenHeightPx * SWIPE_DISTANCE_FRACTION)
        .toInt()
        .coerceAtLeast((24 * context.resources.displayMetrics.density).toInt())

    @Volatile private var enabled = false
    @Volatile private var dailyLimit = ReelsShieldDefaults.DAILY_LIMIT
    @Volatile private var appLimits: Map<String, Int> = emptyMap()
    @Volatile private var trackedPackages: Set<String> = ReelsShieldDefaults.PACKAGES
    @Volatile private var dayStartHour = 0
    @Volatile private var dayStartMinute = 0
    private var adopted = false
    private var dirty = false
    private var todayDate = ""
    private var todayCount = 0
    private var totalCount = 0L
    private val todayByApp = HashMap<String, Int>(8)
    private val history = HashMap<String, Int>(HISTORY_DAYS * 2)
    private val historyByApp = HashMap<String, MutableMap<String, Int>>(HISTORY_DAYS * 2)
    private val scrollTracks = HashMap<String, ScrollTrack>(8)
    private val sessions = HashMap<String, SessionState>(8)
    private val sessionTimeouts = HashMap<String, Runnable>(8)
    private var foregroundPackage = ""
    private var lastWindowKey = ""
    private var surfaceWindowKey = ""
    private var blockedWindowPkg = ""
    private var surfacePackage = ""
    private var surfaceActive = false
    private var surfaceSeenAt = 0L
    private var surfaceConfirmedAt = 0L
    private var surfaceMissStreak = 0
    private var scanPending = false
    private var scanToken = 0
    private var lastScanAt = 0L
    private var lastScanPackage = ""
    private var suppressUntil = 0L
    private var lastBackAt = 0L
    private var sheetVisible = false
    private var sheetVisibleAt = 0L

    private var hintCachePackage = ""
    private var hintCacheAt = 0L
    private var hintCacheValue = false
    private var selectedSurfaceTabPackage = ""
    private var selectedSurfaceTabAt = 0L
    private var structuralPagerPackage = ""
    private var structuralPagerAt = 0L

    private var lastPersistAt = 0L
    private var cachedDateKey = ""
    private var cachedDateKeyAt = 0L

    private val appLabels = ConcurrentHashMap<String, String>()
    private val requestedLabels = ConcurrentHashMap.newKeySet<String>()
    private enum class ScrollSource { VIDEO, UNKNOWN, SHEET, TEXT }

    private class ScrollTrack {
        var lastFrom = -1
        var lastTo = -1
        var hasIndex = false
        var lastScrollY = 0
        var hasScrollY = false
        var lastSwipeAt = 0L

        // One touch gesture can emit dozens of scroll frames, especially for a
        // fling. They are accumulated and judged once after the stream settles.
        var gestureActive = false
        var gestureDistancePx = 0f
        var gestureExplicit = false
        var gestureHadDelta = false
        var gestureKindResolved = false
        var gestureKind = ScrollSource.UNKNOWN
        var lastEventAt = 0L
        var settleRunnable: Runnable? = null
    }

    private class SurfaceScan(
        val matched: Boolean,
        val matchedStrong: Boolean,
        val selectedSurfaceTab: Boolean,
        val otherTabSelected: Boolean,
        val sheetVisible: Boolean,
        val complete: Boolean,
        val pagerVisible: Boolean = false,
        val facebookPagerVisible: Boolean = false,
        val selectedSurfaceTabDetected: Boolean = false
    )

    private class SessionState(
        var swipes: Int = 0,
        var leftAt: Long = 0L
    )

    // ---------------------------------------------------------------- state

    /** Config sync. Safe from any thread; counter adoption happens on main. */
    fun updatePreferences(prefs: UserPreferences) {
        runOnMain {
            enabled = prefs.reelsShieldEnabled && !prefs.accessibilityLimitedMode
            dailyLimit = prefs.reelsShieldDailyLimit.coerceAtLeast(0)
            appLimits = prefs.reelsShieldAppLimits
                .filterKeys { it in ReelsShieldDefaults.SUPPORTED_PACKAGES }
            trackedPackages = prefs.reelsShieldPackages
                .filter { it in ReelsShieldDefaults.SUPPORTED_PACKAGES }
                .toSet()
            dayStartHour = prefs.dayStartHour
            dayStartMinute = prefs.dayStartMinute
            overlay.updateTheme(prefs)

            if (!enabled) {
                deactivateSurface("disabled", flush = true)
                overlay.hideNow()
                return@runOnMain
            }

            preloadLabels(prefs.reelsShieldPackages)
            adopt(prefs)
        }
    }

    private fun adopt(prefs: UserPreferences) {
        val today = currentDateKey()
        if (!adopted) {
            adopted = true
            history.clear()
            history.putAll(prefs.reelsShieldHistory)
            historyByApp.clear()
            prefs.reelsShieldHistoryByApp.forEach { (date, apps) ->
                if (apps.isNotEmpty()) historyByApp[date] = HashMap(apps)
            }
            totalCount = prefs.reelsShieldTotalCount
            if (prefs.reelsShieldTodayDate == today) {
                todayDate = today
                todayCount = prefs.reelsShieldTodayCount
                todayByApp.clear()
                todayByApp.putAll(prefs.reelsShieldTodayByApp)
            } else {
                // The service may start before the midnight worker runs.
                if (prefs.reelsShieldTodayDate.isNotEmpty() && prefs.reelsShieldTodayByApp.isNotEmpty()) {
                    historyByApp[prefs.reelsShieldTodayDate] = HashMap(prefs.reelsShieldTodayByApp)
                }
                todayDate = today
                todayCount = 0
                todayByApp.clear()
                dirty = true
            }
            history[today] = todayCount
            // Publish a stale-day reset right away so the settings card and a
            // later worker run cannot resurrect yesterday's counters.
            if (dirty) persist(force = true)
        }
        rollDayIfNeeded()
    }

    /** Day key is cached so the swipe path never formats a date. */
    private fun currentDateKey(): String {
        val now = System.currentTimeMillis()
        if (cachedDateKey.isEmpty() || now - cachedDateKeyAt > DATE_KEY_CACHE_MS) {
            cachedDateKey = dateKeyFormat.format(
                Date(DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute))
            )
            cachedDateKeyAt = now
        }
        return cachedDateKey
    }

    private fun rollDayIfNeeded() {
        val today = currentDateKey()
        if (todayDate == today) return
        if (todayDate.isNotEmpty()) {
            history[todayDate] = todayCount
            if (todayByApp.isNotEmpty()) historyByApp[todayDate] = HashMap(todayByApp)
        }
        todayDate = today
        todayCount = 0
        todayByApp.clear()
        historyByApp.remove(today)
        history[today] = 0
        clearSessions()
        persist(force = true)
    }

    private fun isPoolLimitReached(): Boolean = dailyLimit > 0 && todayCount >= dailyLimit

    /** True while the selected Reels/Shorts/Spotlight tab is still in front. */
    private fun isSurfaceTabBacked(pkg: String): Boolean =
        selectedSurfaceTabPackage == pkg &&
                System.currentTimeMillis() - selectedSurfaceTabAt <= SURFACE_TAB_STATE_TTL_MS

    /** True while a Facebook structural pager was recently confirmed. */
    private fun isStructuralPagerBacked(pkg: String): Boolean =
        structuralPagerPackage == pkg &&
                System.currentTimeMillis() - structuralPagerAt <= STRUCTURAL_PAGER_TTL_MS

    /**
     * Instagram/Facebook expose a reliable selected-tab state, so leaving the
     * feed can tear the surface down quickly. YouTube's pivot bar is less
     * consistent, so it keeps the longer miss grace.
     */
    private fun tabConflictGraceFor(pkg: String): Long = when (pkg) {
        PACKAGE_INSTAGRAM, PACKAGE_FACEBOOK, PACKAGE_FACEBOOK_LITE -> TAB_CONFLICT_GRACE_MS
        else -> SURFACE_MISS_GRACE_MS
    }

    /**
     * The cap that actually applies to [pkg]. A missing override means the app
     * uses the shared pool; a stored override is always clamped to the pool so
     * stale values can never let one app outlive the pool.
     */
    private fun perAppLimitFor(pkg: String): Int {
        if (dailyLimit <= 0) return 0
        val configured = appLimits[pkg] ?: dailyLimit
        return configured.coerceIn(1, dailyLimit)
    }

    private fun isLimitReached(pkg: String? = null): Boolean {
        if (isPoolLimitReached()) return true
        if (pkg == null) return false
        val perAppLimit = perAppLimitFor(pkg)
        return perAppLimit > 0 && (todayByApp[pkg] ?: 0) >= perAppLimit
    }

    private fun remainingBeforeBack(pkg: String): Int {
        val poolRemaining = if (dailyLimit > 0) {
            (dailyLimit - todayCount).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val perAppLimit = perAppLimitFor(pkg)
        val appRemaining = if (perAppLimit > 0) {
            (perAppLimit - (todayByApp[pkg] ?: 0)).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val remaining = minOf(poolRemaining, appRemaining)
        return if (remaining == Int.MAX_VALUE) -1 else remaining
    }

    fun onScreenOff() {
        runOnMain {
            // Turning the screen off ends the app visit; start the ten-minute
            // session countdown before tearing the surface down.
            val active = surfacePackage.ifEmpty { foregroundPackage }
            if (active.isNotEmpty()) endSessionLater(active)
            deactivateSurface("screen off", flush = true)
            overlay.hideNow()
        }
    }

    fun destroy() {
        runOnMain {
            mainHandler.removeCallbacksAndMessages(null)
            clearSessions()
            deactivateSurface("service destroy", flush = false)
            persist(force = true)
            overlay.destroy()
        }
    }

    private fun clearSessions() {
        sessionTimeouts.values.forEach { mainHandler.removeCallbacks(it) }
        sessionTimeouts.clear()
        sessions.clear()
    }

    // ----------------------------------------------------------- counting

    /**
     * A selected bottom/top navigation tab is the only reliable signal while a
     * Reels/Shorts viewer is idle. Instagram keeps the Reels pager alive on
     * other tabs, so tab state - not pager presence - decides whether the feed
     * is actually in front.
     *
     * Only tab-bar sized nodes are considered. Tapping a random "Reels" card in
     * the home feed must not switch the surface on.
     */
    fun handleViewSelectedEvent(event: AccessibilityEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (!enabled || !adopted) return
        if (SharedMonitoringState.accessibilityLimitedMode) return
        if (!AppStateHolder.isScreenOn.value) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == context.packageName) return
        if (SharedMonitoringState.isPrivatePackage(pkg)) return
        if (pkg !in trackedPackages) return
        // TikTok and its variants are entirely short-video feeds; tab labels
        // there ("For You", "Friends") must not tear the surface down.
        if (pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES) return

        trackForeground(pkg)

        val source = try {
            event.source
        } catch (_: Exception) {
            null
        } ?: return
        try {
            val bounds = Rect()
            try {
                source.getBoundsInScreen(bounds)
            } catch (_: Exception) {
                return
            }
            if (bounds.isEmpty) return
            val inBottomBar = bounds.top >= (screenHeightPx * TAB_BAR_TOP_FRACTION).toInt()
            val inTopBar = bounds.bottom <= (screenHeightPx * (1f - TAB_BAR_TOP_FRACTION)).toInt()
            if (!inBottomBar && !inTopBar) return
            if (bounds.height() > screenHeightPx * TAB_BAR_MAX_HEIGHT_FRACTION) return

            val id = source.viewIdResourceName?.lowercase(Locale.US) ?: ""
            val label = source.contentDescription?.toString()
                ?: paneTitleOf(source)
                ?: nodeTextOf(source)
            val exactSurfaceTab = isSurfaceTabId(pkg, id)
            val tabLike = isTabId(id) || hasTabWord(label) || hasSurfaceTabWord(pkg, label)

            // A top-bar label such as YouTube's "Shorts" filter chip is not a
            // navigation tab; only an exact tab id may activate from up there.
            if (exactSurfaceTab ||
                (inBottomBar && tabLike && hasSurfaceTabWord(pkg, label))
            ) {
                selectedSurfaceTabPackage = pkg
                selectedSurfaceTabAt = System.currentTimeMillis()
                blockedWindowPkg = ""
                OverlayLogBuffer.d("ReelsShield", "surface tab on: $pkg")
                activateSurface(pkg, "surface tab", entered = true)
                scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
                return
            }

            if (inBottomBar && tabLike) {
                if (selectedSurfaceTabPackage == pkg) {
                    selectedSurfaceTabPackage = ""
                    selectedSurfaceTabAt = 0L
                    OverlayLogBuffer.d("ReelsShield", "surface tab off: $pkg")
                }
                if (surfaceActive && surfacePackage == pkg) {
                    deactivateSurface("other tab selected", flush = true)
                }
            }
        } finally {
            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Scroll events are high volume. This method only does cheap event checks;
     * the scrolled container is classified once per gesture and counting is
     * deferred until that gesture settles.
     */
    fun handleScrollEvent(event: AccessibilityEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (!enabled || !adopted) return
        if (SharedMonitoringState.accessibilityLimitedMode) return
        if (!AppStateHolder.isScreenOn.value) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == context.packageName) return
        if (SharedMonitoringState.isPrivatePackage(pkg)) {
            if (surfaceActive) deactivateSurface("private app", flush = true)
            return
        }
        if (pkg !in trackedPackages) {
            if (surfaceActive) deactivateSurface("untracked app", flush = true)
            return
        }

        // A scroll can be the first event after re-entering the app; make sure
        // the visit (and its ten-minute session) is tracked even if the window
        // event was missed.
        trackForeground(pkg)

        val now = System.currentTimeMillis()
        if (now < suppressUntil) return

        rollDayIfNeeded()

        val trusted = pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES
        // Scrolled containers often carry the screen name ("Reels", "Shorts",
        // "Spotlight") in their accessibility description. That is cheap to
        // read and works even when the source hierarchy is unavailable.
        val eventHint = hasSurfaceWord(event.contentDescription?.toString())
        if (!surfaceActive || surfacePackage != pkg) {
            if (trusted) {
                activateSurface(pkg, "short-video app", entered = true)
            } else if (blockedWindowPkg != pkg &&
                (eventHint || cachedSourceHasSurfaceHint(pkg, event.source, now))
            ) {
                // A generic surface hint can also come from a post detail that
                // was opened from the reels feed. A window the scan has not
                // re-confirmed only gets back in through structural evidence.
                activateSurface(pkg, "scroll source", entered = true)
            } else {
                // One bounded scan is enough to decide whether this scroll is
                // happening on a reels/shorts surface; the scan is throttled.
                scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
                return
            }
        }

        if (!surfaceActive || surfacePackage != pkg) return

        val track = scrollTracks.getOrPut(pkg) { ScrollTrack() }
        recordScrollGesture(pkg, event, track, trusted, now)
        // Feeds whose pager ids are obfuscated (Instagram builds, Facebook
        // Lite) confirm themselves through the bounded scan instead of the
        // event source. Refresh it while scrolling so the confirmation window
        // used by the counting gate stays current; the scan is throttled.
        scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
    }

    /**
     * Accumulates every frame of one touch gesture. The container is classified
     * on the first frame, and the decision is made only after the stream
     * settles - so a fling cannot increment once per frame even with the much
     * smaller swipe threshold.
     */
    private fun recordScrollGesture(
        pkg: String,
        event: AccessibilityEvent,
        track: ScrollTrack,
        trusted: Boolean,
        now: Long
    ) {
        val dy = event.scrollDeltaY
        val dx = event.scrollDeltaX
        if (dx != 0 && abs(dx) > abs(dy)) {
            // Horizontal carousels and tab paging are not video page changes.
            resetGesture(track)
            track.lastEventAt = now
            return
        }

        val gap = now - track.lastEventAt
        if (!track.gestureActive || gap > GESTURE_GAP_MS) {
            // New touch: start from a clean slate so index/position evidence
            // never leaks across gestures or between the feed and a comment
            // list that share the same package track.
            resetGesture(track)
            track.gestureActive = true
        }
        track.lastEventAt = now

        if (!track.gestureKindResolved) {
            val source = event.source
            if (source != null) {
                track.gestureKind = classifyScrollSource(pkg, source, trusted, now)
                track.gestureKindResolved = true
                if (track.gestureKind == ScrollSource.SHEET) {
                    // Flag the tray on the first frame, not at settle, so the
                    // counting gate is shut before the gesture can be judged.
                    markSheetVisible("scroll classify")
                }
            }
        }

        val from = event.fromIndex
        val to = event.toIndex
        val hasIndex = from >= 0 || to >= 0
        val indexChanged = hasIndex && track.hasIndex &&
                (from != track.lastFrom || to != track.lastTo)
        if (hasIndex) {
            track.hasIndex = true
            track.lastFrom = from
            track.lastTo = to
        }

        val scrollY = event.scrollY
        val positionJump = track.hasScrollY &&
                abs(scrollY - track.lastScrollY) >= scrollJumpPx
        track.hasScrollY = true
        track.lastScrollY = scrollY

        if (dy != 0) {
            track.gestureHadDelta = true
            track.gestureDistancePx += dy
        }
        if (indexChanged || positionJump) track.gestureExplicit = true

        scheduleSettle(pkg, track)
    }

    private fun scheduleSettle(pkg: String, track: ScrollTrack) {
        track.settleRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { onScrollSettled(pkg, track) }
        track.settleRunnable = runnable
        mainHandler.postDelayed(runnable, SWIPE_SETTLE_MS)
    }

    /**
     * Runs once the scroll stream has been quiet for [SWIPE_SETTLE_MS]. Only
     * one count can come out of a gesture, which lets the distance threshold be
     * small without double counting a fling.
     */
    private fun onScrollSettled(pkg: String, track: ScrollTrack) {
        track.settleRunnable = null
        if (!track.gestureActive) return
        track.gestureActive = false

        val distance = abs(track.gestureDistancePx)
        val hadDelta = track.gestureHadDelta
        val explicit = track.gestureExplicit
        val resolved = track.gestureKindResolved
        val kind = if (track.gestureKindResolved) {
            track.gestureKind
        } else {
            ScrollSource.UNKNOWN
        }
        resetGesture(track)

        if (!enabled || !adopted) return
        if (!AppStateHolder.isScreenOn.value) return
        if (!surfaceActive || surfacePackage != pkg) return
        val now = System.currentTimeMillis()
        if (now < suppressUntil) return

        // A tap or a one-pixel jitter is not a page change. Containers that
        // report no delta at all still fall back to one count per gesture.
        val qualified = explicit || (hadDelta && distance >= swipeDistancePx) || !hadDelta
        if (!qualified) return

        if (kind == ScrollSource.SHEET || kind == ScrollSource.TEXT) {
            markSheetVisible("scroll source")
            return
        }

        // While a comment/caption tray is confirmed, only a scroll that came
        // from the video pager itself may count.
        val sheetOpen = sheetVisible && now - sheetVisibleAt <= SHEET_STATE_TTL_MS
        if (sheetOpen && kind != ScrollSource.VIDEO) return

        val trusted = pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES
        // TikTok: a classified source that is not the feed (comment list,
        // explore grid) never counts. A gesture whose source was unavailable
        // keeps the trusted fallback and leans on the sheet flag instead.
        if (trusted && resolved && kind != ScrollSource.VIDEO) return
        val backed = isSurfaceTabBacked(pkg) || isStructuralPagerBacked(pkg)
        if (!trusted && !backed && kind != ScrollSource.VIDEO &&
            now - surfaceConfirmedAt > SURFACE_FALLBACK_WINDOW_MS
        ) {
            // Ambiguous hierarchy: re-confirm the feed before counting.
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }

        // A pager index change is strong evidence; fallback pagers get a longer
        // cooldown so the tail of one fling cannot increment twice.
        val cooldown = if (explicit || kind == ScrollSource.VIDEO) {
            MIN_SWIPE_INTERVAL_MS
        } else {
            FALLBACK_SWIPE_INTERVAL_MS
        }
        if (now - track.lastSwipeAt < cooldown) return
        track.lastSwipeAt = now
        // A swipe that passed every evidence gate is itself proof the feed is
        // on screen; keep the surface alive through the next video's
        // transition scans, which often come back empty.
        surfaceSeenAt = now
        surfaceMissStreak = 0

        // The scroll demonstrably came from the video pager, so a sheet flag
        // left over from a dismissed tray can be dropped.
        if (kind == ScrollSource.VIDEO) clearSheetVisible()

        if (isLimitReached(pkg)) {
            showPill(pkg, atLimit = true)
            enforceBack(pkg, "swipe at limit")
            return
        }

        todayCount += 1
        todayByApp[pkg] = (todayByApp[pkg] ?: 0) + 1
        totalCount += 1
        history[todayDate] = todayCount
        recordSessionSwipe(pkg)
        persist(force = false)

        showPill(pkg, atLimit = isLimitReached(pkg))
    }

    private fun recordSessionSwipe(pkg: String) {
        val state = sessions.getOrPut(pkg) { SessionState() }
        sessionTimeouts.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        state.leftAt = 0L
        state.swipes += 1
    }

    /**
     * The session belongs to the app visit, not to the Reels tab. In-app window
     * changes keep the same package and therefore keep the session alive;
     * opening another app starts the ten-minute countdown. Returning to a
     * tracked app within that window resumes the session immediately, even if
     * the short-video tab is opened much later during the same visit.
     */
    private fun trackForeground(pkg: String) {
        val previous = foregroundPackage
        foregroundPackage = pkg
        if (previous.isNotEmpty() && previous != pkg) {
            endSessionLater(previous)
        }
        // Also resume when the same package comes back after screen-off; the
        // app can stay "foreground" while the display is off.
        resumeSession(pkg)
    }

    /** Cancels the pending timeout and keeps the swipes when the app is reopened in time. */
    private fun resumeSession(pkg: String) {
        val state = sessions[pkg] ?: return
        sessionTimeouts.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()
        if (state.leftAt > 0 && now - state.leftAt >= SESSION_TIMEOUT_MS) {
            state.swipes = 0
        }
        state.leftAt = 0L
    }

    private fun beginSession(pkg: String) {
        sessions.getOrPut(pkg) { SessionState() }
        resumeSession(pkg)
        OverlayLogBuffer.d("ReelsShield", "session on: $pkg (${sessionSwipes(pkg)} swipes)")
    }

    /**
     * Called when the app visit ends (left to another app or screen off). The
     * session keeps its swipes for ten minutes; if the app is not opened again
     * by then it is finalized and the next visit starts from zero. Repeated
     * calls while the app is already away do not restart the countdown.
     */
    private fun endSessionLater(pkg: String) {
        val state = sessions[pkg] ?: return
        if (state.leftAt > 0L) return
        val leftAt = System.currentTimeMillis()
        state.leftAt = leftAt
        sessionTimeouts.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            sessionTimeouts.remove(pkg)
            val current = sessions[pkg] ?: return@Runnable
            if (current.leftAt == leftAt) {
                sessions.remove(pkg)
                OverlayLogBuffer.d("ReelsShield", "session end: $pkg (${current.swipes} swipes)")
            }
        }
        sessionTimeouts[pkg] = runnable
        mainHandler.postDelayed(runnable, SESSION_TIMEOUT_MS)
    }

    private fun sessionSwipes(pkg: String): Int = sessions[pkg]?.swipes ?: 0

    private fun resetGesture(track: ScrollTrack) {
        track.gestureActive = false
        track.gestureDistancePx = 0f
        track.gestureExplicit = false
        track.gestureHadDelta = false
        track.gestureKindResolved = false
        track.gestureKind = ScrollSource.UNKNOWN
        track.hasIndex = false
        track.lastFrom = -1
        track.lastTo = -1
        track.hasScrollY = false
        track.lastScrollY = 0
    }

    /**
     * Classifies the container that emitted the scroll. Comment trays and
     * caption/description panels can sit inside the same window as the feed, so
     * sheet evidence is checked first and wins over any feed hint.
     */
    private fun classifyScrollSource(
        pkg: String,
        source: AccessibilityNodeInfo,
        trusted: Boolean,
        now: Long
    ): ScrollSource {
        val sheetOpen = sheetVisible && now - sheetVisibleAt <= SHEET_STATE_TTL_MS
        var node: AccessibilityNodeInfo? = source
        var depth = 0
        var large = false
        var video = false
        var videoDepth = -1
        var text = false
        val parents = ArrayList<AccessibilityNodeInfo>(MAX_SOURCE_DEPTH)
        val bounds = Rect()
        try {
            while (node != null && depth < MAX_SOURCE_DEPTH) {
                val id = node.viewIdResourceName?.lowercase(Locale.US) ?: ""
                val className = node.className?.toString()?.lowercase(Locale.US) ?: ""
                val isTextNode = className.contains("textview") ||
                        className.contains("edittext")
                if (isSheetNode(id, className) ||
                    isSheetLabel(node.contentDescription?.toString()) ||
                    isSheetLabel(paneTitleOf(node)) ||
                    (isSheetLabel(nodeTextOf(node)) && !isTextNode)
                ) {
                    return ScrollSource.SHEET
                }
                if (isTextNode) {
                    text = true
                }
                try {
                    node.getBoundsInScreen(bounds)
                    if (bounds.height() >= largeScrollSourcePx &&
                        bounds.width() >= minSurfaceWidthPx
                    ) {
                        large = true
                    }
                    if (bounds.height() >= minSurfaceHeightPx &&
                        bounds.width() >= minSurfaceWidthPx
                    ) {
                        if ((node.isVisibleToUser && isKnownPagerNode(pkg, node)) ||
                            isStructuralFacebookPager(pkg, node, bounds) ||
                            isFullScreenPagePager(node, bounds, depth) ||
                            matchesStrongSurfaceHint(node) ||
                            isFullScreenPager(node, bounds) ||
                            (depth == 0 && hasFullPageChild(node)) ||
                            // TikTok's feed is recognised by shape at any depth:
                            // full-page children. A comment list or explore grid
                            // fills the screen with short rows and never matches.
                            (trusted && node.isVisibleToUser &&
                                    hasMostlyFullPageChildren(node, bounds))
                        ) {
                            video = true
                            if (videoDepth < 0 || depth < videoDepth) videoDepth = depth
                        }
                    }
                } catch (_: Exception) {
                }
                val parent = try { node.parent } catch (_: Exception) { null }
                if (parent != null) parents.add(parent)
                node = parent
                depth++
            }
        } finally {
            parents.forEach { parent -> try { parent.recycle() } catch (_: Exception) {} }
        }
        // A page-sized video container wins over a TextView seen on the way up
        // the ancestry: every reel carries a caption, so a text hit alone would
        // veto the count on exactly the apps whose pager ids are obfuscated.
        // While a comment tray is open only a pager directly under the scroll
        // may count: a video container found higher up is the reel behind the
        // tray, not the list being read.
        if (video && (!sheetOpen || videoDepth <= 1)) return ScrollSource.VIDEO
        // Trusted short-video apps (TikTok) are judged before the caption/TextView
        // veto, but only when the gesture carried no source node - a classified
        // source must already have been feed-shaped to reach VIDEO above. A
        // comment list or explore grid must not ride the trusted flag.
        if (trusted && !sheetOpen && source == null) return ScrollSource.VIDEO
        if (text) return ScrollSource.TEXT
        // A selected Reels/Shorts tab (or a confirmed Facebook structural
        // pager) is reliable even when the scroll event carries no source. The
        // sheet check above still wins, so comments and captions never count.
        // Fallback evidence cannot vouch for a trusted app (TikTok): its
        // comment tray fills the screen just like the feed and the tab/grace
        // hints would vouch for it. Trusted apps count only through feed-shaped
        // pager evidence above; anything else stays UNKNOWN and is discarded
        // by the trusted gate in onScrollSettled.
        if (!trusted && !sheetOpen && (isSurfaceTabBacked(pkg) || isStructuralPagerBacked(pkg))) {
            return ScrollSource.VIDEO
        }
        if (!trusted && !sheetOpen && large && surfaceActive &&
            now - surfaceConfirmedAt <= SURFACE_SOURCE_GRACE_MS
        ) {
            return ScrollSource.VIDEO
        }
        return ScrollSource.UNKNOWN
    }

    /**
     * A pager's direct child is one full-screen page, while a comment list's
     * children are short rows. This separates the video pager from overlays
     * even when the app exposes no useful view ids.
     */
    private fun hasFullPageChild(node: AccessibilityNodeInfo): Boolean {
        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        if (childCount <= 0) return false
        val childBounds = Rect()
        for (index in 0 until minOf(childCount, MAX_PAGER_CHILDREN)) {
            val child = try { node.getChild(index) } catch (_: Exception) { null } ?: continue
            try {
                child.getBoundsInScreen(childBounds)
                if (childBounds.height() >= screenHeightPx * FULLSCREEN_PAGER_FRACTION &&
                    childBounds.width() >= minSurfaceWidthPx
                ) {
                    return true
                }
            } catch (_: Exception) {
                // Keep checking the remaining children.
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        return false
    }

    private fun isSheetNode(id: String, className: String): Boolean =
        id.contains("comment") || id.contains("caption") || id.contains("description") ||
                id.contains("reply") || id.contains("sheet") || id.contains("dialog") ||
                id.contains("drawer") || id.contains("modal") ||
                className.contains("dialog") || className.contains("popup") ||
                className.contains("bottomsheet") || className.contains("modal") ||
                className.contains("drawer")

    /**
     * Short labels such as "Comments", "Replies" or "Description" identify an
     * overlay pane. Long captions are ignored so a video caption cannot be
     * mistaken for the pane itself.
     */
    private fun isSheetLabel(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.trim().lowercase(Locale.US)
        if (text.length > MAX_SHEET_LABEL_LENGTH) return false
        // Whole-word match so "Add a comment..." / "1.234 Komentar" count too.
        var start = -1
        for (index in text.indices) {
            if (text[index].isLetterOrDigit() && start < 0) {
                start = index
            } else if (!text[index].isLetterOrDigit() && start >= 0) {
                if (text.substring(start, index) in SHEET_TEXT_WORDS) return true
                start = -1
            }
        }
        return start >= 0 && text.substring(start) in SHEET_TEXT_WORDS
    }

    private fun hasTabWord(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.trim().lowercase(Locale.US)
        if (text.length > MAX_TAB_LABEL_LENGTH) return false
        return hasWordInText(text, NON_SURFACE_TAB_WORDS)
    }

    private fun hasWordInText(text: String, words: Set<String>): Boolean {
        var start = -1
        for (index in text.indices) {
            val wordChar = text[index].isLetterOrDigit()
            if (wordChar && start < 0) {
                start = index
            } else if (!wordChar && start >= 0) {
                if (text.substring(start, index) in words) return true
                start = -1
            }
        }
        return start >= 0 && text.substring(start) in words
    }

    private fun markSheetVisible(reason: String) {
        val now = System.currentTimeMillis()
        if (!sheetVisible || now - sheetVisibleAt > SHEET_STATE_TTL_MS) {
            OverlayLogBuffer.d("ReelsShield", "sheet on: $reason")
        }
        sheetVisible = true
        sheetVisibleAt = now
    }

    private fun clearSheetVisible() {
        if (!sheetVisible) return
        sheetVisible = false
        sheetVisibleAt = 0L
        OverlayLogBuffer.d("ReelsShield", "sheet off")
    }

    // ------------------------------------------------------- surface state

    /**
     * Called from window-state events and package-change monitoring. The
     * window class catches activity-based reel viewers before the content is
     * fully laid out.
     */
    fun onForegroundChanged(
        pkg: String,
        windowClassName: String? = null,
        windowTitle: String? = null
    ) {
        runOnMain {
            trackForeground(pkg)
            if (!enabled) {
                deactivateSurface("disabled", flush = true)
                return@runOnMain
            }
            if (!AppStateHolder.isScreenOn.value) {
                deactivateSurface("screen off", flush = true)
                return@runOnMain
            }
            if (pkg == context.packageName || SharedMonitoringState.isPrivatePackage(pkg)) {
                deactivateSurface("left to $pkg", flush = true)
                return@runOnMain
            }
            if (pkg !in trackedPackages) {
                deactivateSurface("left to $pkg", flush = true)
                return@runOnMain
            }

            if (surfacePackage.isNotEmpty() && surfacePackage != pkg) {
                deactivateSurface("switched to $pkg", flush = true)
            }

            rollDayIfNeeded()

            // Opening the comment tray or an expanded description can arrive as
            // its own window while the video surface stays behind it. Keep the
            // surface alive, but flag the tray so its scrolls never count.
            if (looksLikeSheetWindow(windowClassName) || isSheetLabel(windowTitle)) {
                markSheetVisible("window")
                if (surfaceActive && surfacePackage == pkg) return@runOnMain
            }

            if (pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES) {
                activateSurface(pkg, "short-video app", entered = true)
                // Trusted feeds still need the overlay scan so comment trays
                // can suppress counting.
                scheduleSurfaceScan(pkg, delayMs = WINDOW_SETTLE_MS, deactivateIfMissing = false)
                return@runOnMain
            }
            if (looksLikeSurfaceWindow(pkg, windowClassName) || hasSurfaceWord(windowTitle)) {
                blockedWindowPkg = ""
                activateSurface(pkg, "window label", entered = true)
                return@runOnMain
            }

            // A different window inside the same app (post detail, photo
            // viewer) is not the reels surface. Tab/pager evidence belonged to
            // the previous window: drop it and tear the surface down, so only
            // a fresh scan can turn counting back on.
            val windowKey = if (windowClassName.isNullOrBlank() && windowTitle.isNullOrBlank()) {
                ""
            } else {
                "$pkg|${windowClassName.orEmpty()}|${windowTitle.orEmpty()}"
            }
            if (windowKey.isNotEmpty()) {
                if (lastWindowKey.isNotEmpty() && lastWindowKey.startsWith("$pkg|") &&
                    lastWindowKey != windowKey
                ) {
                    selectedSurfaceTabPackage = ""
                    selectedSurfaceTabAt = 0L
                    structuralPagerPackage = ""
                    structuralPagerAt = 0L
                    blockedWindowPkg = pkg
                    lastWindowKey = windowKey
                    if (surfaceActive && surfacePackage == pkg) {
                        deactivateSurface("in-app window", flush = true)
                    }
                    OverlayLogBuffer.d("ReelsShield", "window break: $windowKey")
                    // The new window may itself be the reels screen (Facebook
                    // opens Reels in its own activity); confirm it right away
                    // instead of waiting for the first scroll.
                    scheduleSurfaceScan(pkg, delayMs = WINDOW_SETTLE_MS, deactivateIfMissing = false)
                    return@runOnMain
                }
                lastWindowKey = windowKey
            }

            val deactivateIfMissing = !surfaceActive || surfacePackage != pkg
            scheduleSurfaceScan(pkg, delayMs = WINDOW_SETTLE_MS, deactivateIfMissing = deactivateIfMissing)
        }
    }

    fun onContentChanged(event: AccessibilityEvent, pkg: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (!enabled || !adopted) return
        if (pkg !in trackedPackages) return
        if (SharedMonitoringState.isPrivatePackage(pkg)) return
        if (!AppStateHolder.isScreenOn.value) return
        if (System.currentTimeMillis() < suppressUntil) return

        rollDayIfNeeded()

        if (pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES) {
            if (!surfaceActive || surfacePackage != pkg) {
                activateSurface(pkg, "short-video app", entered = true)
            }
            // Refresh the comment/description tray state while the feed is
            // open. The scan itself is throttled.
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }

        val now = System.currentTimeMillis()
        if (blockedWindowPkg != pkg && cachedSourceHasSurfaceHint(pkg, event.source, now)) {
            activateSurface(pkg, "content source", entered = true)
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }
        // A pane/tab label in a content event is useful evidence, but on its own
        // it can also be a Reels preview card on a home feed. Use it to scan
        // immediately and let the bounded walk confirm a real feed surface.
        val contentHint = hasSurfaceWord(event.contentDescription?.toString())

        val activeHere = surfaceActive && surfacePackage == pkg
        val minInterval = if (activeHere) ACTIVE_SCAN_INTERVAL_MS else IDLE_SCAN_INTERVAL_MS
        if (contentHint) {
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }
        if (now - lastScanAt < minInterval && lastScanPackage == pkg) return
        if (!activeHere && !isLimitReached()) {
            // Outside a reels surface there is nothing to count. Keep a slow
            // confirmation scan for the pill, but never walk the tree on every
            // ordinary feed refresh.
            if (now - lastScanAt < IDLE_SCAN_INTERVAL_MS) return
        }
        scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
    }

    private fun scheduleSurfaceScan(pkg: String, delayMs: Long, deactivateIfMissing: Boolean) {
        if (!enabled) return
        if (scanPending) return
        scanPending = true
        val token = ++scanToken
        mainHandler.postDelayed({
            if (token != scanToken) return@postDelayed
            scanPending = false
            scanSurface(pkg, deactivateIfMissing)
        }, delayMs)
    }

    private fun scanSurface(pkg: String, deactivateIfMissing: Boolean) {
        if (!enabled || pkg !in trackedPackages) return
        if (!AppStateHolder.isScreenOn.value) {
            if (surfaceActive) deactivateSurface("screen off", flush = true)
            return
        }
        val now = System.currentTimeMillis()
        if (now < suppressUntil && !surfaceActive) return
        if (lastScanPackage == pkg && now - lastScanAt < MIN_SCAN_INTERVAL_MS) {
            if (deactivateIfMissing && surfaceActive && surfacePackage == pkg &&
                now - surfaceSeenAt > SURFACE_MISS_GRACE_MS
            ) {
                deactivateSurface("scan miss", flush = true)
            }
            return
        }
        lastScanAt = now
        lastScanPackage = pkg

        val root = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        if (root == null || root.packageName?.toString() != pkg) {
            // A transient window transition must not tear the pill down while
            // the surface was confirmed moments ago; see SURFACE_MISS_GRACE_MS.
            if (deactivateIfMissing && surfaceActive && surfacePackage == pkg &&
                now - surfaceSeenAt > SURFACE_MISS_GRACE_MS
            ) {
                deactivateSurface("window changed", flush = true)
            }
            return
        }

        val trusted = pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES
        val scan = findShortVideoSurface(pkg, root, trusted)

        if (scan.otherTabSelected && !scan.selectedSurfaceTab &&
            selectedSurfaceTabPackage == pkg
        ) {
            selectedSurfaceTabPackage = ""
            selectedSurfaceTabAt = 0L
            OverlayLogBuffer.d("ReelsShield", "surface tab off: $pkg (scan)")
        } else if (scan.selectedSurfaceTabDetected) {
            selectedSurfaceTabPackage = pkg
            selectedSurfaceTabAt = now
        }

        if (scan.facebookPagerVisible) {
            structuralPagerPackage = pkg
            structuralPagerAt = now
        } else if (structuralPagerPackage == pkg && scan.complete) {
            // A completed walk that no longer sees the pager drops the backing,
            // so a post detail's scroll cannot inherit the reels confirmation.
            structuralPagerPackage = ""
            structuralPagerAt = 0L
        }

        // A visible comment/caption tray suppresses every scroll that does not
        // come from the video pager itself. A complete scan that no longer sees
        // a tray clears the flag, so closing comments restores counting.
        if (scan.sheetVisible) {
            markSheetVisible("surface scan")
        } else if (scan.complete) {
            clearSheetVisible()
        }

        // After an in-app window break (post detail, photo viewer) only
        // positive evidence may turn counting back on: the structural pager in
        // the new window is exactly what must not reactivate it. A strong
        // surface hint, a re-detected surface tab, or a return to the window
        // where the feed was confirmed releases the block.
        val reactivationAllowed = blockedWindowPkg != pkg ||
                scan.matchedStrong ||
                scan.selectedSurfaceTab ||
                // Facebook's Reels screen is its own window with no stable tab
                // ids and often no surface title. Its identifying mark is the
                // screen-filling, vertically stacked page pager - post details
                // and photo viewers never match it (single page, short rows or
                // a horizontal carousel), so it may release the block.
                scan.facebookPagerVisible ||
                (surfaceWindowKey.isNotEmpty() && lastWindowKey == surfaceWindowKey)
        if (scan.matched && reactivationAllowed) {
            surfaceSeenAt = now
            surfaceConfirmedAt = now
            surfaceMissStreak = 0
            activateSurface(pkg, "surface scan", entered = true)
        } else if (surfaceActive && surfacePackage == pkg) {
            if (scan.otherTabSelected && !scan.selectedSurfaceTab &&
                now - surfaceSeenAt > tabConflictGraceFor(pkg)
            ) {
                // The selected bottom tab moved to Home/Search/Profile, so the
                // short-video tab is no longer in front. A short grace window
                // absorbs a transient tab report during a reel-to-reel
                // transition without leaving the pill counting on the feed.
                deactivateSurface("other tab", flush = true)
            } else if (scan.selectedSurfaceTab) {
                // A selected Reels/Shorts/Spotlight tab keeps an already
                // confirmed feed alive while its pager settles. An unselected
                // tab on the home feed deliberately does not.
                surfaceSeenAt = now
                surfaceMissStreak = 0
                showPill(pkg, atLimit = isLimitReached(pkg))
            } else if (scan.facebookPagerVisible) {
                // Facebook exposes no stable tab ids. Once a structural pager
                // scroll activated the surface, keep it alive while that pager
                // is still on screen.
                surfaceSeenAt = now
                surfaceMissStreak = 0
                showPill(pkg, atLimit = isLimitReached(pkg))
            } else {
                surfaceMissStreak += 1
                // A video transition or a partially rebuilt tree can make many
                // consecutive scans come back empty while the user is still on
                // the feed. A selected surface tab refreshes surfaceSeenAt on
                // every scan, so the pill only goes away once the feed has
                // truly not been seen for SURFACE_MISS_GRACE_MS after multiple consecutive misses.
                if ((deactivateIfMissing || surfaceMissStreak >= 4) &&
                    now - surfaceSeenAt > SURFACE_MISS_GRACE_MS
                ) {
                    deactivateSurface("left surface", flush = true)
                }
            }
        }
    }

    private fun activateSurface(pkg: String, reason: String, entered: Boolean) {
        val wasActive = surfaceActive && surfacePackage == pkg
        if (!wasActive && surfacePackage.isNotEmpty() && surfacePackage != pkg) {
            scrollTracks.remove(surfacePackage)
        }
        val now = System.currentTimeMillis()
        surfacePackage = pkg
        // Any confirmed activation re-opens the in-app window block: the feed
        // was found again, so hint evidence is trustworthy once more.
        blockedWindowPkg = ""
        if (lastWindowKey.isNotEmpty()) surfaceWindowKey = lastWindowKey
        surfaceActive = true
        surfaceSeenAt = now
        surfaceConfirmedAt = now
        surfaceMissStreak = 0

        if (!wasActive && entered) {
            hintCachePackage = ""
            scrollTracks.remove(pkg)
            beginSession(pkg)
            OverlayLogBuffer.d("ReelsShield", "surface on: $pkg ($reason)")
            val atLimit = isLimitReached(pkg)
            // Show the remaining number as soon as the short-video tab opens,
            // not only after the first swipe.
            showPill(pkg, atLimit = atLimit)
            if (atLimit) {
                enforceBack(pkg, "surface entry")
            }
        } else if (wasActive) {
            showPill(pkg, atLimit = isLimitReached(pkg))
        }
    }

    private fun deactivateSurface(reason: String, flush: Boolean) {
        if (!surfaceActive && surfacePackage.isEmpty()) {
            if (flush) flushIfDirty()
            return
        }
        val previous = surfacePackage
        surfaceActive = false
        surfacePackage = ""
        surfaceSeenAt = 0L
        surfaceConfirmedAt = 0L
        surfaceMissStreak = 0
        hintCachePackage = ""
        sheetVisible = false
        sheetVisibleAt = 0L
        structuralPagerPackage = ""
        structuralPagerAt = 0L
        scanToken++
        scanPending = false
        if (previous.isNotEmpty()) {
            // Closing the Reels/Shorts tab does not end the app visit. The
            // session timeout is started by trackForeground()/onScreenOff().
            scrollTracks.remove(previous)
            OverlayLogBuffer.d("ReelsShield", "surface off: $previous ($reason)")
        }
        overlay.hideNow()
        if (flush) flushIfDirty()
    }

    /**
     * Bounded breadth-first walk. The queue owns every node it receives, so all
     * of them are recycled in one place, including on early return.
     *
     * The walk deliberately does not stop at the first feed match: it also
     * records whether a selected Reels/Shorts/Spotlight tab is present (needed
     * before trusting a plain full-screen pager) and whether a comment or
     * description tray is covering the feed.
     */
    private fun findShortVideoSurface(
        pkg: String,
        root: AccessibilityNodeInfo,
        trusted: Boolean
    ): SurfaceScan {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val owned = ArrayList<AccessibilityNodeInfo>(MAX_SURFACE_NODES)
        queue.add(root)
        owned.add(root)
        var visited = 0
        // The window label is the accessibility title of the active screen. A
        // dedicated Reels/Shorts activity can be confirmed from it before the
        // pager content is laid out.
        var strongMatch = trusted || hasSurfaceWord(windowTitleOf(root))
        var weakMatch = false
        var selectedSurfaceTab = trusted
        var otherTabSelected = false
        var sheetVisible = false
        var pagerFound = false
        var pagerVisible = false
        var pagerPages = false
        var facebookPagerVisible = false
        val bounds = Rect()

        // Instagram's hierarchy is deep enough that a plain BFS can hit the
        // node cap before reaching either the pager or the tab bar. Exact ids
        // are a cheap binder lookup, so resolve them before walking.
        val exactPagers = findKnownPagerNodes(root, pkg)
        try {
            exactPagers.forEach { node ->
                if (!node.isVisibleToUser) return@forEach
                try {
                    node.getBoundsInScreen(bounds)
                } catch (_: Exception) {
                    return@forEach
                }
                if (bounds.height() >= minSurfaceHeightPx &&
                    bounds.width() >= minSurfaceWidthPx
                ) {
                    pagerVisible = true
                    if (hasMostlyFullPageChildren(node, bounds)) pagerPages = true
                }
            }
        } finally {
            exactPagers.forEach { node -> try { node.recycle() } catch (_: Exception) {} }
        }

        try {
            while (queue.isNotEmpty() && visited < MAX_SURFACE_NODES) {
                val node = queue.removeFirst()
                visited++
                val nodeBounds: Rect? = try {
                    node.getBoundsInScreen(bounds)
                    Rect(bounds)
                } catch (_: Exception) {
                    null
                }
                if (nodeBounds != null) {
                    val large = nodeBounds.height() >= minSurfaceHeightPx &&
                            nodeBounds.width() >= minSurfaceWidthPx
                    val visible = node.isVisibleToUser
                    // Instagram keeps its Reels fragment in the tree on other
                    // tabs. Only the exact pager (above), the selected Reels tab,
                    // or a dedicated window title may confirm it; a generic
                    // "reel" class/description is not enough.
                    if (large && visible && pkg != PACKAGE_INSTAGRAM &&
                        matchesStrongSurfaceHint(node)
                    ) {
                        strongMatch = true
                    } else if (large && visible && matchesSurfaceTextHint(node)) {
                        weakMatch = true
                    }
                    if (large && visible && isKnownPagerNode(pkg, node)) {
                        pagerVisible = true
                        if (hasMostlyFullPageChildren(node, nodeBounds)) pagerPages = true
                    }
                    if (large && isStructuralFacebookPager(pkg, node, nodeBounds)) {
                        facebookPagerVisible = true
                    }
                    if (large && visible && isFullScreenPager(node, nodeBounds)) {
                        pagerFound = true
                    }
                    if (visible && isOverlayPane(node, nodeBounds)) sheetVisible = true
                    if (!selectedSurfaceTab && isSelectedSurfaceTab(pkg, node)) {
                        selectedSurfaceTab = true
                    }
                    if (!otherTabSelected && isOtherTabSelected(node, nodeBounds)) {
                        otherTabSelected = true
                    }
                }
                if (node.childCount > 0 && visited < MAX_SURFACE_NODES) {
                    for (index in 0 until node.childCount) {
                        val child = try {
                            node.getChild(index)
                        } catch (_: Exception) {
                            null
                        }
                        if (child != null) {
                            queue.add(child)
                            owned.add(child)
                            if (owned.size >= MAX_SURFACE_NODES) break
                        }
                    }
                }
            }
        } finally {
            owned.forEach { node -> try { node.recycle() } catch (_: Exception) {} }
        }
        // The scan may miss the tab bar on a very large tree. Fall back to the
        // last TYPE_VIEW_SELECTED tab only while the walk has not seen a
        // different tab selected.
        val tabFromEvent = selectedSurfaceTabPackage == pkg &&
                System.currentTimeMillis() - selectedSurfaceTabAt <= SURFACE_TAB_STATE_TTL_MS &&
                !otherTabSelected
        val effectiveSurfaceTab = selectedSurfaceTab || tabFromEvent
        // Only a walk that reached the end of the tree saw the whole tab bar.
        val scanComplete = queue.isEmpty() && visited < MAX_SURFACE_NODES
        // A selected bottom tab that is not Reels/Shorts/Spotlight means the
        // user already left the feed. A visible full-screen pager over Home
        // (a reel opened from the feed) still wins.
        val tabConflict = otherTabSelected && !effectiveSurfaceTab
        // A screen-filling structural pager may never override a selected
        // Home/Search tab: Facebook's obfuscated feed RecyclerView can look
        // page-sized, and Instagram keeps its reels pager alive off-tab. With
        // no conflicting tab it stays strong evidence of the feed.
        val facebookPagerMatch = facebookPagerVisible && !tabConflict
        // A plain full-screen pager is not enough by itself: Snapchat Stories
        // and photo carousels use the same primitive. It must be paired with
        // the selected Spotlight/Reels/Shorts tab. Instagram additionally keeps
        // its pager alive on other tabs, so a visible pager only counts while
        // the Reels tab is selected, the surface was already activated by a
        // scroll, or a complete walk saw no conflicting tab - several builds
        // never report the Reels tab as selected at all.
        val pagerMatch = if (pkg == PACKAGE_INSTAGRAM) {
            // A pager Instagram keeps alive on other tabs must not confirm the
            // feed: it only counts when its pages are actually laid out (an
            // idle home-tab pager has none) and no Home/Search tab is in front.
            pagerVisible && pagerPages && !tabConflict &&
                    (effectiveSurfaceTab || surfaceActive || scanComplete)
        } else {
            pagerVisible
        }
        val matched = strongMatch ||
                pagerMatch ||
                facebookPagerMatch ||
                (!tabConflict && (weakMatch || (pagerFound && effectiveSurfaceTab)))
        return SurfaceScan(
            matched = matched,
            matchedStrong = strongMatch,
            selectedSurfaceTab = effectiveSurfaceTab,
            otherTabSelected = otherTabSelected,
            sheetVisible = sheetVisible,
            // Only a walk that actually reached the end of the tree may clear
            // a previously detected tray. Hitting the node cap is inconclusive.
            complete = scanComplete,
            pagerVisible = pagerVisible,
            facebookPagerVisible = facebookPagerMatch,
            selectedSurfaceTabDetected = selectedSurfaceTab
        )
    }

    /** A selected bottom navigation tab that is not Reels/Shorts/Spotlight. */
    private fun isOtherTabSelected(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val selected = try {
            node.isSelected || node.isChecked
        } catch (_: Exception) {
            false
        }
        if (!selected) return false
        // An immersive reel viewer hides the bottom bar; a stale selected Home
        // item behind it must not count as a tab conflict.
        try {
            if (!node.isVisibleToUser) return false
        } catch (_: Exception) {
        }
        if (bounds.top < screenHeightPx * TAB_BAR_TOP_FRACTION) return false
        if (bounds.height() > screenHeightPx * TAB_BAR_MAX_HEIGHT_FRACTION) return false
        val label = paneTitleOf(node)
            ?: node.contentDescription?.toString()
            ?: nodeTextOf(node)
            ?: return false
        return hasTabWord(label)
    }

    /**
     * A comment/caption bottom sheet: either an explicit sheet/dialog class or
     * a panel tall enough to cover the feed. The height gate keeps small
     * "Comments" buttons from being mistaken for the tray.
     */
    private fun isOverlayPane(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val id = node.viewIdResourceName?.lowercase(Locale.US) ?: ""
        val className = node.className?.toString()?.lowercase(Locale.US) ?: ""
        val isTextNode = className.contains("textview") ||
                className.contains("edittext")
        if (!isSheetNode(id, className) &&
            !isSheetLabel(node.contentDescription?.toString()) &&
            !isSheetLabel(paneTitleOf(node)) &&
            !(isSheetLabel(nodeTextOf(node)) && !isTextNode)
        ) {
            return false
        }
        if (className.contains("bottomsheet") || className.contains("dialog") ||
            className.contains("popup") || className.contains("modal") ||
            className.contains("drawer")
        ) {
            return true
        }
        return bounds.height() >= sheetPaneMinHeightPx
    }

    /** The currently selected Reels/Shorts/Spotlight navigation tab. */
    private fun isSelectedSurfaceTab(pkg: String, node: AccessibilityNodeInfo): Boolean {
        val selected = try {
            node.isSelected || node.isChecked
        } catch (_: Exception) {
            false
        }
        if (!selected) return false
        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
        } catch (_: Exception) {
            return false
        }
        // Bottom navigation only. A selected "Shorts" filter chip at the top
        // of YouTube Home is not the Shorts tab.
        if (bounds.top < screenHeightPx * TAB_BAR_TOP_FRACTION) return false
        if (bounds.height() > screenHeightPx * TAB_BAR_MAX_HEIGHT_FRACTION) return false
        return hasSurfaceTabWord(pkg, paneTitleOf(node)) ||
                hasSurfaceTabWord(pkg, node.contentDescription?.toString()) ||
                hasSurfaceTabWord(pkg, nodeTextOf(node))
    }

    /** Cheap ancestry check used on scroll/content events. */
    private fun sourceHasSurfaceHint(pkg: String, source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        // The window title is set by the app's activity/pane and is the
        // cheapest navigation-level signal available on a scroll event.
        if (hasSurfaceWord(windowTitleOf(source))) return true
        var node: AccessibilityNodeInfo? = source
        var depth = 0
        // Every parent fetched is owned here, including the last one, so they
        // are all recycled on both the match and the miss path.
        val parents = ArrayList<AccessibilityNodeInfo>(MAX_SOURCE_DEPTH)
        val bounds = Rect()
        try {
            while (node != null && depth < MAX_SOURCE_DEPTH) {
                try {
                    node.getBoundsInScreen(bounds)
                } catch (_: Exception) {
                    return false
                }
                val large = bounds.height() >= minSurfaceHeightPx &&
                        bounds.width() >= minSurfaceWidthPx
                // Exact pager ids and Facebook's structural pager are stronger
                // than a caption word: Instagram keeps its pager alive on other
                // tabs, so visibility is required before trusting it.
                // Pager shape alone may only re-confirm a surface a scan has
                // already backed (selected tab or structural pager); fresh
                // activation goes through the bounded scan, so opening a post
                // detail or photo viewer cannot switch the counter on by itself.
                val backed = isSurfaceTabBacked(pkg) || isStructuralPagerBacked(pkg)
                if (large && node.isVisibleToUser && backed &&
                    (isKnownPagerNode(pkg, node) || isStructuralFacebookPager(pkg, node, bounds))
                ) {
                    return true
                }
                // Obfuscated pager containers (Instagram builds without the
                // clips ids, Facebook Lite) are recognised by shape instead.
                if (backed && isFullScreenPagePager(node, bounds, depth)) return true
                // A plain full-screen pager is ambiguous (Stories, photo
                // carousels). Only an explicit Reels/Shorts marker may activate
                // the surface from a scroll source; the bounded scan confirms
                // tab-backed pagers.
                if (large && matchesStrongSurfaceHint(node)) return true

                val parent = try { node.parent } catch (_: Exception) { null }
                if (parent != null) parents.add(parent)
                node = parent
                depth++
            }
        } finally {
            parents.forEach { parent -> try { parent.recycle() } catch (_: Exception) {} }
        }
        return false
    }

    /**
     * Ancestry walks cost binder calls, so scroll/content bursts share a short
     * cache. It expires before a typical second swipe, so evidence from one
     * gesture is never reused for the next one.
     */
    private fun cachedSourceHasSurfaceHint(
        pkg: String,
        source: AccessibilityNodeInfo?,
        now: Long
    ): Boolean {
        if (source == null) return false
        if (hintCachePackage == pkg && now - hintCacheAt < SURFACE_HINT_CACHE_MS) {
            return hintCacheValue
        }
        val result = sourceHasSurfaceHint(pkg, source)
        hintCachePackage = pkg
        hintCacheAt = now
        hintCacheValue = result
        return result
    }

    /**
     * Structural surface hints: view ids, class names, window/pane titles and
     * accessibility descriptions. These are set by the screen container rather
     * than by caption text, so they can confirm a feed even when the app hides
     * which bottom tab is selected.
     */
    private fun matchesStrongSurfaceHint(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase(Locale.US) ?: ""
        val className = node.className?.toString()?.lowercase(Locale.US) ?: ""
        if (id.isNotEmpty() && SURFACE_ID_HINTS.any { id.contains(it) }) return true
        if (className.contains("shortvideo") || className.contains("reels") ||
            className.contains("shorts") || className.contains("spotlight")
        ) {
            return true
        }
        if (hasSurfaceWord(windowTitleOf(node))) return true
        if (hasSurfaceWord(paneTitleOf(node))) return true
        return hasSurfaceWord(node.contentDescription?.toString())
    }

    /**
     * Visible label text ("Reels", "Shorts"). Useful evidence, but a home feed
     * can show the same word on a preview card, so it stays weaker than
     * [matchesStrongSurfaceHint] and cannot override a selected non-video tab.
     */
    private fun matchesSurfaceTextHint(node: AccessibilityNodeInfo): Boolean =
        hasSurfaceWord(nodeTextOf(node))

    private fun paneTitleOf(node: AccessibilityNodeInfo): String? = try {
        node.paneTitle?.toString()
    } catch (_: Exception) {
        null
    }

    private fun nodeTextOf(node: AccessibilityNodeInfo): String? = try {
        node.text?.toString()
    } catch (_: Exception) {
        null
    }

    private fun windowTitleOf(node: AccessibilityNodeInfo): String? = try {
        node.window?.title?.toString()
    } catch (_: Exception) {
        null
    }

    /**
     * Matches whole navigation words ("Reels", "Shorts", "Spotlight") and the
     * phrase "short video". Captions are ignored unless they are short enough
     * to be a tab/window label, which keeps false positives down.
     */
    private fun hasSurfaceWord(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.lowercase(Locale.US)
        if (text.length > MAX_SURFACE_TEXT_LENGTH) return false
        if (text.contains("short video") || text.contains("short videos")) return true
        var start = -1
        for (index in text.indices) {
            val wordChar = text[index].isLetterOrDigit()
            if (wordChar && start < 0) {
                start = index
            } else if (!wordChar && start >= 0) {
                if (text.substring(start, index) in SURFACE_TEXT_WORDS) return true
                start = -1
            }
        }
        return start >= 0 && text.substring(start) in SURFACE_TEXT_WORDS
    }

    private fun isFullScreenPager(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val className = node.className?.toString()?.lowercase(Locale.US) ?: ""
        if (!className.contains("viewpager")) return false
        return bounds.height() >= screenHeightPx * FULLSCREEN_PAGER_FRACTION &&
                bounds.width() >= screenWidthPx * MIN_SURFACE_WIDTH_FRACTION
    }

    /**
     * Exact ids are the strongest signal an app can give. Instagram keeps its
     * pager alive on other tabs, so callers must still require visibility.
     */
    private fun isKnownPagerNode(pkg: String, node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase(Locale.US) ?: return false
        val name = id.substringAfterLast('/')
        return when (pkg) {
            PACKAGE_INSTAGRAM ->
                name == "clips_viewer_view_pager" || name == "clips_viewer_pager" ||
                        name.contains("clips_viewer") || name.contains("reels_pager")
            PACKAGE_YOUTUBE ->
                name == "reel_recycler" || name == "reel_watch_fragment_root" ||
                        name == "shorts_container"
            else -> false
        }
    }

    private fun findKnownPagerNodes(
        root: AccessibilityNodeInfo,
        pkg: String
    ): List<AccessibilityNodeInfo> {
        val names = when (pkg) {
            PACKAGE_INSTAGRAM -> listOf("clips_viewer_view_pager", "clips_viewer_pager")
            PACKAGE_YOUTUBE -> listOf(
                "reel_recycler",
                "reel_watch_fragment_root",
                "shorts_container"
            )
            else -> return emptyList()
        }
        val result = ArrayList<AccessibilityNodeInfo>(names.size)
        names.forEach { name ->
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$name")
            } catch (_: Exception) {
                null
            }
            if (!nodes.isNullOrEmpty()) result.addAll(nodes)
        }
        return result
    }

    /**
     * Facebook (and Lite) obfuscate view names, so Reels is recognised by
     * structure: a full-screen, scrollable RecyclerView whose direct child is a
     * full page. A feed row is not page-sized, which keeps the home feed out.
     */
    private fun isStructuralFacebookPager(
        pkg: String,
        node: AccessibilityNodeInfo,
        bounds: Rect
    ): Boolean {
        if (pkg != PACKAGE_FACEBOOK && pkg != PACKAGE_FACEBOOK_LITE) return false
        val className = node.className?.toString() ?: return false
        if (!className.lowercase(Locale.US).endsWith("recyclerview")) {
            return false
        }
        val scrollable = try {
            node.isScrollable
        } catch (_: Exception) {
            false
        }
        if (!scrollable) return false
        if (!node.isVisibleToUser) return false
        if (bounds.width() < screenWidthPx * FACEBOOK_PAGER_MIN_WIDTH_FRACTION) return false
        if (bounds.height() < screenHeightPx * FACEBOOK_PAGER_MIN_HEIGHT_FRACTION) return false

        val id = node.viewIdResourceName
        if (!id.isNullOrEmpty()) {
            if (!id.startsWith("$pkg:id/")) return false
        } else if (pkg != PACKAGE_FACEBOOK_LITE) {
            // Katana exposes resource ids; an unnamed RecyclerView there is a
            // feed, not the Reels pager.
            return false
        }

        val childCount = try {
            node.childCount
        } catch (_: Exception) {
            0
        }
        if (childCount <= 0 || childCount > MAX_PAGER_CHILDREN) return false
        return hasMostlyFullPageChildren(node, bounds)
    }

    /**
     * Instagram obfuscates its reels container ids on some builds and Facebook
     * Lite renames every view, so the exact pager lookups can miss. A visible,
     * screen-filling RecyclerView whose attached children are full pages has
     * the same shape as the Facebook structural pager, and a feed row, an
     * explore grid cell or a comment list never matches it.
     *
     * Only the scrolled node itself and its direct parent are considered, so a
     * nested pager deep inside another screen cannot activate the surface.
     */
    private fun isFullScreenPagePager(
        node: AccessibilityNodeInfo,
        bounds: Rect,
        depth: Int
    ): Boolean {
        if (depth > 1) return false
        val className = node.className?.toString()?.lowercase(Locale.US) ?: return false
        if (!className.endsWith("recyclerview")) return false
        try {
            if (!node.isVisibleToUser) return false
        } catch (_: Exception) {
            return false
        }
        if (bounds.width() < screenWidthPx * MIN_SURFACE_WIDTH_FRACTION) return false
        if (bounds.height() < screenHeightPx * FULLSCREEN_PAGER_FRACTION) return false
        return hasMostlyFullPageChildren(node, bounds)
    }

    /**
     * A Reels pager's attached pages fill the pager; a feed RecyclerView also
     * contains short rows. A footer/loader is tolerated, but at least two
     * thirds of the sampled children must be nearly as tall as the container
     * itself, which a feed row can never be.
     */
    private fun hasMostlyFullPageChildren(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        if (childCount <= 0) return false
        val sampled = minOf(childCount, MAX_PAGER_CHILDREN)
        var fullPage = 0
        var firstFullTop = 0
        var firstFullBottom = 0
        var stackedPages = false
        val childBounds = Rect()
        for (index in 0 until sampled) {
            val child = try { node.getChild(index) } catch (_: Exception) { null } ?: continue
            try {
                child.getBoundsInScreen(childBounds)
                if (childBounds.height() >= bounds.height() * PAGER_PAGE_HEIGHT_RATIO &&
                    childBounds.width() >= screenWidthPx * FACEBOOK_PAGER_MIN_WIDTH_FRACTION
                ) {
                    if (fullPage == 0) {
                        firstFullTop = childBounds.top
                        firstFullBottom = childBounds.bottom
                    } else if (abs(childBounds.top - firstFullTop) > PAGER_STACK_EPSILON_PX ||
                        abs(childBounds.bottom - firstFullBottom) > PAGER_STACK_EPSILON_PX
                    ) {
                        // Pages at different vertical offsets are stacked: a
                        // real vertical feed pager.
                        stackedPages = true
                    }
                    fullPage++
                }
            } catch (_: Exception) {
                // Keep checking the remaining children.
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        // The ratio already rejects a content list where only one row is
        // media-tall among several short rows, so the original single-page
        // acceptance is safe: only a container whose children are nearly all
        // full pages matches. Side-by-side pages become suspicious only when
        // there are at least two of them, so the vertical-stack check applies
        // from fullPage == 2 upward.
        return fullPage > 0 && fullPage * 3 >= sampled * 2 &&
                (fullPage < 2 || stackedPages)
    }

    private fun isTabId(id: String): Boolean {
        if (id.isEmpty()) return false
        val name = id.substringAfterLast('/')
        return name == "tab" || name.endsWith("_tab") || name.contains("_tab_")
    }

    private fun isSurfaceTabId(pkg: String, id: String): Boolean {
        if (id.isEmpty()) return false
        val name = id.substringAfterLast('/')
        return when (pkg) {
            PACKAGE_INSTAGRAM ->
                name == "clips_tab" || name == "reels_tab" || name == "reel_tab"
            PACKAGE_YOUTUBE ->
                name == "shorts_tab" || name.startsWith("shorts_tab_")
            PACKAGE_FACEBOOK, PACKAGE_FACEBOOK_LITE ->
                (name.contains("reels") || name.contains("reel")) && name.contains("tab")
            else ->
                name == "reels_tab" || name == "clips_tab" ||
                        name == "shorts_tab" || name == "spotlight_tab"
        }
    }

    /**
     * Facebook renamed its Reels tab to "Video" in some versions. Only a
     * tab-bar label may use this looser word set, never a caption or pane.
     */
    private fun hasSurfaceTabWord(pkg: String, raw: String?): Boolean {
        if (hasSurfaceWord(raw)) return true
        if (pkg != PACKAGE_FACEBOOK && pkg != PACKAGE_FACEBOOK_LITE) return false
        if (raw.isNullOrBlank()) return false
        val text = raw.trim().lowercase(Locale.US)
        if (text.length > MAX_TAB_LABEL_LENGTH) return false
        return hasWordInText(text, FACEBOOK_SURFACE_TAB_WORDS)
    }

    private fun looksLikeSurfaceWindow(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        val name = className.lowercase(Locale.US)
        if (name.contains("clips") || name.contains("reels") ||
            name.contains("shorts") || name.contains("spotlight") ||
            name.contains("shortvideo")
        ) {
            return true
        }
        // YouTube's Shorts player uses the singular "reel". Instagram's
        // Stories viewer can also contain "reel", so keep this package-scoped.
        return pkg == PACKAGE_YOUTUBE && name.contains("reel")
    }

    private fun looksLikeSheetWindow(className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        val name = className.lowercase(Locale.US)
        return name.contains("bottomsheet") || name.contains("bottom_sheet") ||
                name.contains("dialog") || name.contains("popup") ||
                name.contains("modal") || name.contains("drawer") ||
                name.contains("comment")
    }

    // ------------------------------------------------------- limit checks

    private fun enforceBack(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < BACK_DEBOUNCE_MS) return
        lastBackAt = now
        suppressUntil = now + ENTRY_SUPPRESS_MS
        val perAppLimit = perAppLimitFor(pkg)
        OverlayLogBuffer.d(
            "ReelsShield",
            "limit reached (pool $todayCount/$dailyLimit, app ${todayByApp[pkg] ?: 0}/$perAppLimit) ($reason) -> back"
        )
        mainHandler.post {
            try {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (_: Exception) {
            }
        }
        // Keep the pill visible long enough to explain the kick, then hide it
        // and let the next foreground/window event decide what comes next.
        mainHandler.postDelayed({
            if (surfacePackage == pkg) deactivateSurface("after back", flush = true)
        }, BACK_PILL_LINGER_MS)
    }

    private fun showPill(pkg: String, atLimit: Boolean) {
        overlay.show(
            pool = todayCount,
            limit = dailyLimit,
            session = sessionSwipes(pkg),
            remaining = remainingBeforeBack(pkg),
            label = labelFor(pkg),
            atLimit = atLimit
        )
    }

    private fun labelFor(pkg: String): String {
        appLabels[pkg]?.let { return it }
        if (requestedLabels.add(pkg)) {
            persistScope.launch {
                val label = try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    ReelsShieldDefaults.fallbackLabel(pkg)
                }
                appLabels[pkg] = label
            }
        }
        return ReelsShieldDefaults.fallbackLabel(pkg)
    }

    private fun preloadLabels(packages: Set<String>) {
        packages.forEach { pkg ->
            if (appLabels.containsKey(pkg) || !requestedLabels.add(pkg)) return@forEach
            persistScope.launch {
                val label = try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    ReelsShieldDefaults.fallbackLabel(pkg)
                }
                appLabels[pkg] = label
            }
        }
    }

    // ----------------------------------------------------------- persisting

    private fun flushIfDirty() {
        if (dirty) persist(force = true)
    }

    /** Main thread only; serialized DataStore writes keep snapshots in order. */
    private fun persist(force: Boolean) {
        dirty = true
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistAt = now
        dirty = false

        history[todayDate] = todayCount
        val trimmedKeys = history.keys.sortedDescending().take(HISTORY_DAYS)
        val trimmed = HashMap<String, Int>(trimmedKeys.size)
        trimmedKeys.forEach { key -> history[key]?.let { trimmed[key] = it } }
        history.keys.retainAll(trimmedKeys.toSet())

        val trimmedByApp = HashMap<String, Map<String, Int>>(trimmedKeys.size)
        trimmedKeys.forEach { key ->
            val apps = historyByApp[key]
            if (!apps.isNullOrEmpty()) trimmedByApp[key] = HashMap(apps)
        }
        historyByApp.keys.retainAll(trimmedByApp.keys)

        val date = todayDate
        val count = todayCount
        val total = totalCount
        val byApp = HashMap(todayByApp)

        persistScope.launch {
            try {
                preferencesRepository.setReelsShieldCounters(date, count, byApp, total, trimmed, trimmedByApp)
            } catch (e: Exception) {
                runOnMain { dirty = true }
                OverlayLogBuffer.w("ReelsShield", "persist failed: ${e.message}")
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        private const val MIN_SWIPE_INTERVAL_MS = 320L
        private const val FALLBACK_SWIPE_INTERVAL_MS = 620L
        private const val SWIPE_SETTLE_MS = 180L
        private const val GESTURE_GAP_MS = 260L
        private const val DATE_KEY_CACHE_MS = 30_000L
        private const val PERSIST_INTERVAL_MS = 3_000L
        private const val BACK_DEBOUNCE_MS = 2_000L
        private const val BACK_PILL_LINGER_MS = 900L
        private const val ENTRY_SUPPRESS_MS = 2_500L
        private const val SESSION_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val WINDOW_SETTLE_MS = 260L
        private const val MIN_SCAN_INTERVAL_MS = 650L
        private const val ACTIVE_SCAN_INTERVAL_MS = 1_000L
        private const val IDLE_SCAN_INTERVAL_MS = 2_000L
        private const val SURFACE_MISS_GRACE_MS = 10_000L
        private const val TAB_CONFLICT_GRACE_MS = 1_500L
        private const val SURFACE_HINT_CACHE_MS = 400L
        private const val SURFACE_TAB_STATE_TTL_MS = 2 * 60 * 1_000L
        private const val STRUCTURAL_PAGER_TTL_MS = 60_000L
        private const val SURFACE_FALLBACK_WINDOW_MS = 1_200L
        private const val SURFACE_SOURCE_GRACE_MS = 2_500L
        private const val SHEET_STATE_TTL_MS = 3_000L
        private const val MAX_SURFACE_NODES = 400
        private const val MAX_SOURCE_DEPTH = 12
        private const val MAX_PAGER_CHILDREN = 4
        private const val HISTORY_DAYS = 30
        private const val MAX_SURFACE_TEXT_LENGTH = 64
        private const val MAX_SHEET_LABEL_LENGTH = 48
        private const val MAX_TAB_LABEL_LENGTH = 56

        private const val MIN_SURFACE_HEIGHT_FRACTION = 0.50f
        private const val MIN_SURFACE_WIDTH_FRACTION = 0.60f
        private const val FULLSCREEN_PAGER_FRACTION = 0.78f
        private const val LARGE_SOURCE_FRACTION = 0.28f
        private const val SCROLL_JUMP_FRACTION = 0.30f
        private const val SWIPE_DISTANCE_FRACTION = 0.07f
        private const val SHEET_PANE_HEIGHT_FRACTION = 0.18f
        private const val TAB_BAR_TOP_FRACTION = 0.70f
        private const val TAB_BAR_MAX_HEIGHT_FRACTION = 0.18f
        private const val FACEBOOK_PAGER_MIN_WIDTH_FRACTION = 0.90f
        private const val FACEBOOK_PAGER_MIN_HEIGHT_FRACTION = 0.75f

        /** A pager page fills its container; a feed row never comes close. */
        private const val PAGER_PAGE_HEIGHT_RATIO = 0.85f
        private const val PAGER_STACK_EPSILON_PX = 8

        private const val PACKAGE_INSTAGRAM = "com.instagram.android"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_FACEBOOK = "com.facebook.katana"
        private const val PACKAGE_FACEBOOK_LITE = "com.facebook.lite"

        private val SURFACE_ID_HINTS = listOf(
            "reels", "shorts", "short_video", "shortvideo",
            "spotlight", "clips", "clip_"
        )
        private val SURFACE_TEXT_WORDS = setOf(
            "reel", "reels", "shorts", "spotlight", "clips"
        )
        private val SHEET_TEXT_WORDS = setOf(
            "comment", "comments", "reply", "replies", "caption", "description",
            // Indonesian UI: "Komentar", "Balasan", "Keterangan".
            "komentar", "balasan", "keterangan", "deskripsi"
        )
        private val NON_SURFACE_TAB_WORDS = setOf(
            "home", "search", "explore", "profile", "inbox", "chat", "chats",
            "camera", "stories", "friends", "marketplace", "notifications",
            "menu", "subscriptions", "library", "create", "activity", "you",
            "watch",
            // Indonesian bottom tabs. Without "beranda" the Home tab is never
            // seen as a conflict, so the feed keeps counting off-Reels.
            "beranda", "jelajahi", "eksplorasi", "telusuri", "cari", "profil",
            "akun", "pesan", "kotak", "notifikasi", "pemberitahuan", "teman",
            "belanja", "langganan", "pustaka", "aktivitas", "tonton", "nonton",
            "cerita", "obrolan", "buat"
        )
        private val FACEBOOK_SURFACE_TAB_WORDS = setOf("video", "videos")
    }
}
