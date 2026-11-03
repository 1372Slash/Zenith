package com.etrisad.zenith.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.data.preferences.ReelsShieldDefaults
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.reels.ReelAppConfig
import com.etrisad.zenith.service.reels.ReelCountTracker
import com.etrisad.zenith.service.reels.ReelScriptRunner
import com.etrisad.zenith.ui.components.overlay.ReelsShieldOverlayManager
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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

    private val reelEventScope =
        CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val reelEventQueue =
        Channel<AccessibilityEvent>(Channel.CONFLATED) { event -> recycleEvent(event) }
    private val reelScriptRunner = ReelScriptRunner(service).also { it.setup() }
    private val reelCountTracker = ReelCountTracker { pkg -> runOnMain { onScriptReelCounted(pkg) } }
    private var pendingReelSettleEvent: AccessibilityEvent? = null
    private val reelSettleRecheck = Runnable {
        val event = pendingReelSettleEvent ?: return@Runnable
        pendingReelSettleEvent = null
        queueReelEvent(event)
    }
    init {
        reelEventScope.launch {
            for (event in reelEventQueue) {
                try {
                    processReelScriptEvent(event)
                } catch (_: Exception) {
                } finally {
                    recycleEvent(event)
                }
            }
        }
    }

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
                reelCountTracker.resetAll()
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
                if (prefs.reelsShieldTodayDate.isNotEmpty() && prefs.reelsShieldTodayByApp.isNotEmpty()) {
                    historyByApp[prefs.reelsShieldTodayDate] = HashMap(prefs.reelsShieldTodayByApp)
                }
                todayDate = today
                todayCount = 0
                todayByApp.clear()
                dirty = true
            }
            history[today] = todayCount
            if (dirty) persist(force = true)
        }
        rollDayIfNeeded()
    }

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

    private fun isSurfaceTabBacked(pkg: String): Boolean =
        selectedSurfaceTabPackage == pkg &&
                System.currentTimeMillis() - selectedSurfaceTabAt <= SURFACE_TAB_STATE_TTL_MS

    private fun isStructuralPagerBacked(pkg: String): Boolean =
        structuralPagerPackage == pkg &&
                System.currentTimeMillis() - structuralPagerAt <= STRUCTURAL_PAGER_TTL_MS

    private fun tabConflictGraceFor(pkg: String): Long = when (pkg) {
        PACKAGE_INSTAGRAM, PACKAGE_FACEBOOK, PACKAGE_FACEBOOK_LITE -> TAB_CONFLICT_GRACE_MS
        else -> SURFACE_MISS_GRACE_MS
    }

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
            val active = surfacePackage.ifEmpty { foregroundPackage }
            if (active.isNotEmpty()) {
                endSessionLater(active)
                reelCountTracker.onLeftApp(active)
            }
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
            pendingReelSettleEvent?.let { recycleEvent(it) }
            pendingReelSettleEvent = null
            reelEventQueue.close()
            reelEventScope.cancel()
        }
    }

    private fun clearSessions() {
        sessionTimeouts.values.forEach { mainHandler.removeCallbacks(it) }
        sessionTimeouts.clear()
        sessions.clear()
    }

    fun handleViewSelectedEvent(event: AccessibilityEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (!enabled || !adopted) return
        if (SharedMonitoringState.accessibilityLimitedMode) return
        if (!AppStateHolder.isScreenOn.value) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == context.packageName) return
        if (SharedMonitoringState.isPrivatePackage(pkg)) return
        if (pkg !in trackedPackages) return
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

        trackForeground(pkg)
        if (reelCountTracker.isScriptSupported(pkg)) return

        val now = System.currentTimeMillis()
        if (now < suppressUntil) return

        rollDayIfNeeded()

        val trusted = pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES
        val eventHint = hasSurfaceWord(event.contentDescription?.toString())
        if (!surfaceActive || surfacePackage != pkg) {
            if (trusted) {
                activateSurface(pkg, "short-video app", entered = true)
            } else if (blockedWindowPkg != pkg &&
                (eventHint || cachedSourceHasSurfaceHint(pkg, event.source, now))
            ) {
                activateSurface(pkg, "scroll source", entered = true)
            } else {
                scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
                return
            }
        }

        if (!surfaceActive || surfacePackage != pkg) return

        val track = scrollTracks.getOrPut(pkg) { ScrollTrack() }
        recordScrollGesture(pkg, event, track, trusted, now)
        scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
    }

    fun handleScriptEvent(event: AccessibilityEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        Log.d("ZenithReelsDebug", "handleScriptEvent ENTRY pkg=${event.packageName} type=${event.eventType} enabled=$enabled adopted=$adopted")
        if (!enabled || !adopted) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP not enabled/adopted")
            return
        }
        if (SharedMonitoringState.accessibilityLimitedMode) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP accessibilityLimitedMode")
            return
        }
        if (!AppStateHolder.isScreenOn.value) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP screen off")
            return
        }

        val pkg = event.packageName?.toString() ?: return
        Log.d("ZenithReelsDebug", "handleScriptEvent pkg=$pkg tracked=${pkg in trackedPackages} suppressUntil=$suppressUntil now=${System.currentTimeMillis()}")
        if (pkg == context.packageName) return
        if (SharedMonitoringState.isPrivatePackage(pkg)) return
        if (pkg !in trackedPackages) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP pkg not tracked: $pkg")
            return
        }
        val data = ReelAppConfig.dataFor(pkg) ?: run {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP no ReelAppConfig data for: $pkg")
            return
        }
        if ((event.eventType and data.eventType) == 0) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP eventType mismatch: event=${event.eventType} data=${data.eventType}")
            return
        }
        if (System.currentTimeMillis() < suppressUntil) {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP suppressed until $suppressUntil")
            return
        }

        trackForeground(pkg)

        if (reelCountTracker.onPagerEvent(event)) {
            Log.d("ZenithReelsDebug", "handleScriptEvent onPagerEvent TRUE for $pkg, scheduling settle recheck")
            scheduleReelSettleRecheck(event)
        } else {
            Log.d("ZenithReelsDebug", "handleScriptEvent onPagerEvent FALSE for $pkg")
        }

        val copy = try {
            AccessibilityEvent.obtain(event)
        } catch (_: Exception) {
            null
        } ?: run {
            Log.d("ZenithReelsDebug", "handleScriptEvent SKIP failed to copy event for $pkg")
            return
        }
        Log.d("ZenithReelsDebug", "handleScriptEvent queueing event for $pkg")
        queueReelEvent(copy)
    }

    private fun scheduleReelSettleRecheck(event: AccessibilityEvent) {
        mainHandler.removeCallbacks(reelSettleRecheck)
        pendingReelSettleEvent?.let { recycleEvent(it) }
        pendingReelSettleEvent = try {
            AccessibilityEvent.obtain(event)
        } catch (_: Exception) {
            null
        }
        if (pendingReelSettleEvent != null) {
            mainHandler.postDelayed(reelSettleRecheck, ReelCountTracker.PAGER_SETTLE_RECHECK_MS)
        }
    }

    private fun queueReelEvent(event: AccessibilityEvent) {
        if (reelEventQueue.trySend(event).isFailure) recycleEvent(event)
    }

    private fun recycleEvent(event: AccessibilityEvent) {
        try {
            event.recycle()
        } catch (_: Exception) {
        }
    }

    private fun processReelScriptEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        Log.d("ZenithReelsDebug", "processReelScriptEvent ENTRY pkg=$pkg")
        if (!reelCountTracker.isScriptSupported(pkg)) {
            Log.d("ZenithReelsDebug", "processReelScriptEvent SKIP not script supported: $pkg")
            return
        }

        val detection = reelScriptRunner.detect(event)
        Log.d("ZenithReelsDebug", "processReelScriptEvent detect result pkg=$pkg detection=${detection?.let { "comparator=" + it.comparator + " isNull=" + (it.comparator.isNullOrEmpty()) } }")
        reelCountTracker.onEvent(event, detection?.comparator)

        runOnMain {
            Log.d("ZenithReelsDebug", "processReelScriptEvent runOnMain pkg=$pkg enabled=$enabled adopted=$adopted tracked=${trackedPackages.contains(pkg)} screenOn=${AppStateHolder.isScreenOn.value} detectionNull=${detection == null}")
            if (!enabled || !adopted) return@runOnMain
            if (!trackedPackages.contains(pkg)) return@runOnMain
            if (!AppStateHolder.isScreenOn.value) return@runOnMain
            if (detection == null) {
                onScriptViewerGone(pkg)
            } else {
                onScriptViewerVisible(pkg)
            }
        }
    }

    private fun onScriptViewerVisible(pkg: String) {
        val now = System.currentTimeMillis()
        if (!surfaceActive || surfacePackage != pkg) {
            activateSurface(pkg, "script viewer", entered = true)
        } else {
            surfaceSeenAt = now
            surfaceMissStreak = 0
            showPill(pkg, atLimit = isLimitReached(pkg))
        }
    }

    private fun onScriptViewerGone(pkg: String) {
        if (surfaceActive && surfacePackage == pkg) {
            deactivateSurface("script viewer gone", flush = true)
        }
    }

    private fun onScriptReelCounted(pkg: String) {
        if (!enabled || !adopted) return
        if (!trackedPackages.contains(pkg)) return
        if (!AppStateHolder.isScreenOn.value) return
        if (System.currentTimeMillis() < suppressUntil) return

        rollDayIfNeeded()

        val wasActive = surfaceActive && surfacePackage == pkg
        if (!wasActive) {
            activateSurface(pkg, "script count", entered = true)
        } else {
            val now = System.currentTimeMillis()
            surfaceSeenAt = now
            surfaceMissStreak = 0
        }

        if (isLimitReached(pkg)) {
            showPill(pkg, atLimit = true)
            enforceBack(pkg, "script limit")
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
            resetGesture(track)
            track.lastEventAt = now
            return
        }

        val gap = now - track.lastEventAt
        if (!track.gestureActive || gap > GESTURE_GAP_MS) {
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

        val qualified = explicit || (hadDelta && distance >= swipeDistancePx) || !hadDelta
        if (!qualified) return

        if (kind == ScrollSource.SHEET || kind == ScrollSource.TEXT) {
            markSheetVisible("scroll source")
            return
        }

        val sheetOpen = sheetVisible && now - sheetVisibleAt <= SHEET_STATE_TTL_MS
        if (sheetOpen && kind != ScrollSource.VIDEO) return

        val trusted = pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES
        if (trusted && resolved && kind != ScrollSource.VIDEO) return
        val backed = isSurfaceTabBacked(pkg) || isStructuralPagerBacked(pkg)
        if (!trusted && !backed && kind != ScrollSource.VIDEO &&
            now - surfaceConfirmedAt > SURFACE_FALLBACK_WINDOW_MS
        ) {
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }

        val cooldown = if (explicit || kind == ScrollSource.VIDEO) {
            MIN_SWIPE_INTERVAL_MS
        } else {
            FALLBACK_SWIPE_INTERVAL_MS
        }
        if (now - track.lastSwipeAt < cooldown) return
        track.lastSwipeAt = now
        surfaceSeenAt = now
        surfaceMissStreak = 0

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

    private fun trackForeground(pkg: String) {
        val previous = foregroundPackage
        foregroundPackage = pkg
        if (previous.isNotEmpty() && previous != pkg) {
            endSessionLater(previous)
            reelCountTracker.onLeftApp(previous)
        }
        resumeSession(pkg)
    }

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
        if (video && (!sheetOpen || videoDepth <= 1)) return ScrollSource.VIDEO
        if (trusted && !sheetOpen && source == null) return ScrollSource.VIDEO
        if (text) return ScrollSource.TEXT
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

    private fun isSheetLabel(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.trim().lowercase(Locale.US)
        if (text.length > MAX_SHEET_LABEL_LENGTH) return false
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

            if (looksLikeSheetWindow(windowClassName) || isSheetLabel(windowTitle)) {
                markSheetVisible("window")
                if (surfaceActive && surfacePackage == pkg) return@runOnMain
            }

            if (pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES) {
                activateSurface(pkg, "short-video app", entered = true)
                scheduleSurfaceScan(pkg, delayMs = WINDOW_SETTLE_MS, deactivateIfMissing = false)
                return@runOnMain
            }
            if (looksLikeSurfaceWindow(pkg, windowClassName) || hasSurfaceWord(windowTitle)) {
                blockedWindowPkg = ""
                activateSurface(pkg, "window label", entered = true)
                return@runOnMain
            }

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
        if (reelCountTracker.isScriptSupported(pkg)) return

        rollDayIfNeeded()

        if (pkg in ReelsShieldDefaults.SHORT_VIDEO_ONLY_PACKAGES) {
            if (!surfaceActive || surfacePackage != pkg) {
                activateSurface(pkg, "short-video app", entered = true)
            }
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }

        val now = System.currentTimeMillis()
        if (blockedWindowPkg != pkg && cachedSourceHasSurfaceHint(pkg, event.source, now)) {
            activateSurface(pkg, "content source", entered = true)
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }
        val contentHint = hasSurfaceWord(event.contentDescription?.toString())

        val activeHere = surfaceActive && surfacePackage == pkg
        val minInterval = if (activeHere) ACTIVE_SCAN_INTERVAL_MS else IDLE_SCAN_INTERVAL_MS
        if (contentHint) {
            scheduleSurfaceScan(pkg, delayMs = 0L, deactivateIfMissing = false)
            return
        }
        if (now - lastScanAt < minInterval && lastScanPackage == pkg) return
        if (!activeHere && !isLimitReached()) {
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
            structuralPagerPackage = ""
            structuralPagerAt = 0L
        }

        if (scan.sheetVisible) {
            markSheetVisible("surface scan")
        } else if (scan.complete) {
            clearSheetVisible()
        }

        val reactivationAllowed = blockedWindowPkg != pkg ||
                scan.matchedStrong ||
                scan.selectedSurfaceTab ||
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
                deactivateSurface("other tab", flush = true)
            } else if (scan.selectedSurfaceTab) {
                surfaceSeenAt = now
                surfaceMissStreak = 0
                showPill(pkg, atLimit = isLimitReached(pkg))
            } else if (scan.facebookPagerVisible) {
                surfaceSeenAt = now
                surfaceMissStreak = 0
                showPill(pkg, atLimit = isLimitReached(pkg))
            } else {
                surfaceMissStreak += 1
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
            scrollTracks.remove(previous)
            OverlayLogBuffer.d("ReelsShield", "surface off: $previous ($reason)")
        }
        overlay.hideNow()
        if (flush) flushIfDirty()
    }

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
        val tabFromEvent = selectedSurfaceTabPackage == pkg &&
                System.currentTimeMillis() - selectedSurfaceTabAt <= SURFACE_TAB_STATE_TTL_MS &&
                !otherTabSelected
        val effectiveSurfaceTab = selectedSurfaceTab || tabFromEvent
        val scanComplete = queue.isEmpty() && visited < MAX_SURFACE_NODES
        val tabConflict = otherTabSelected && !effectiveSurfaceTab
        val facebookPagerMatch = facebookPagerVisible && !tabConflict
        val pagerMatch = if (pkg == PACKAGE_INSTAGRAM) {
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
            complete = scanComplete,
            pagerVisible = pagerVisible,
            facebookPagerVisible = facebookPagerMatch,
            selectedSurfaceTabDetected = selectedSurfaceTab
        )
    }

    private fun isOtherTabSelected(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val selected = try {
            node.isSelected || node.isChecked
        } catch (_: Exception) {
            false
        }
        if (!selected) return false
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
        if (bounds.top < screenHeightPx * TAB_BAR_TOP_FRACTION) return false
        if (bounds.height() > screenHeightPx * TAB_BAR_MAX_HEIGHT_FRACTION) return false
        return hasSurfaceTabWord(pkg, paneTitleOf(node)) ||
                hasSurfaceTabWord(pkg, node.contentDescription?.toString()) ||
                hasSurfaceTabWord(pkg, nodeTextOf(node))
    }

    private fun sourceHasSurfaceHint(pkg: String, source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        if (hasSurfaceWord(windowTitleOf(source))) return true
        var node: AccessibilityNodeInfo? = source
        var depth = 0
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
                val backed = isSurfaceTabBacked(pkg) || isStructuralPagerBacked(pkg)
                if (large && node.isVisibleToUser && backed &&
                    (isKnownPagerNode(pkg, node) || isStructuralFacebookPager(pkg, node, bounds))
                ) {
                    return true
                }
                if (backed && isFullScreenPagePager(node, bounds, depth)) return true
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
                        stackedPages = true
                    }
                    fullPage++
                }
            } catch (_: Exception) {
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
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

    private fun isTikTokPackage(pkg: String): Boolean {
        return pkg == "com.zhiliaoapp.musically" ||
            pkg == "com.zhiliaoapp.musically.go" ||
            pkg == "com.ss.android.ugc.trill" ||
            pkg == "com.ss.android.ugc.aweme"
    }

    private fun enforceBack(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < BACK_DEBOUNCE_MS) return
        lastBackAt = now
        suppressUntil = now + ENTRY_SUPPRESS_MS
        val perAppLimit = perAppLimitFor(pkg)
        val isTikTok = isTikTokPackage(pkg)
        OverlayLogBuffer.d(
            "ReelsShield",
            "limit reached (pool $todayCount/$dailyLimit, app ${todayByApp[pkg] ?: 0}/$perAppLimit) ($reason) -> " + if (isTikTok) "home" else "back"
        )
        mainHandler.post {
            try {
                if (isTikTok) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            } catch (_: Exception) {
            }
        }
        mainHandler.postDelayed({
            if (surfacePackage == pkg) deactivateSurface("after limit action", flush = true)
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

    private fun flushIfDirty() {
        if (dirty) persist(force = true)
    }

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
            "komentar", "balasan", "keterangan", "deskripsi"
        )
        private val NON_SURFACE_TAB_WORDS = setOf(
            "home", "search", "explore", "profile", "inbox", "chat", "chats",
            "camera", "stories", "friends", "marketplace", "notifications",
            "menu", "subscriptions", "library", "create", "activity", "you",
            "watch",
            "beranda", "jelajahi", "eksplorasi", "telusuri", "cari", "profil",
            "akun", "pesan", "kotak", "notifikasi", "pemberitahuan", "teman",
            "belanja", "langganan", "pustaka", "aktivitas", "tonton", "nonton",
            "cerita", "obrolan", "buat"
        )
        private val FACEBOOK_SURFACE_TAB_WORDS = setOf("video", "videos")
    }
}
