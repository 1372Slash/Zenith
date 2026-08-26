package com.etrisad.zenith.ui.components

import com.etrisad.zenith.ui.navigation.Screen

object FeatureInfoRegistry {

    fun infoFor(route: String?, category: String? = null): FeatureInfo? = when {
        route == Screen.Home.route -> home
        route == Screen.Focus.route -> focus
        route == Screen.Settings.route -> settings
        route == Screen.UsageStats.route -> usageStats
        route == Screen.Bedtime.route -> bedtime
        route == Screen.Alarm.route -> alarm
        route == Screen.GracePeriod.route -> gracePeriod
        route == Screen.EyeCare.route -> eyeCare
        route?.startsWith("app_detail") == true -> appDetail
        route == Screen.Lockdown.route -> lockdown
        route == Screen.Pomodoro.route -> pomodoro
        route == Screen.PausePoint.route -> pausePoint
        route == Screen.PausePointQr.route -> pausePointQr
        route == Screen.OverlayAppearance.route -> overlayAppearance
        route == Screen.GSFlexCustomizer.route -> gsFlexCustomizer
        route == Screen.DatabaseDebug.route -> databaseDebug
        route == Screen.DataRepairment.route -> dataRepairment
        route == Screen.SystemUsageDebug.route -> systemUsageDebug
        route == Screen.FontTest.route -> fontTest
        route?.startsWith("settings_category") == true -> when (category?.lowercase()) {
            "features" -> featuresCategory
            "appearance" -> appearanceCategory
            "performance" -> performanceCategory
            "data management" -> dataManagementCategory
            "developer" -> developerCategory
            else -> null
        }
        else -> null
    }

    private val home = FeatureInfo(
        title = "Home",
        summary = "Your daily dashboard for screen time, streaks, and quick access to everything Zenith offers.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Shows today's screen time against your daily target with a live progress bar and current streak.",
                "Compares today with yesterday and highlights your weekly trend.",
                "Surfaces your active Goals and Shields so you always know what is protecting your attention."
            ),
            FeatureInfoSections.whatYoullSee(
                "Animated usage dashboard with a wavy progress bar, streak flame badge, and Yesterday/Trend cards.",
                "Usage history calendar (tap a bar to inspect a specific day) and an expandable Top Used Apps list.",
                "Quick Actions row (Bedtime, Alarm, Stats, Pomodoro) and Apps/Websites tabs with active Goals & Shields."
            ),
            FeatureInfoSections.tips(
                "Tap the dashboard to change your daily target — presets like 2h/4h/6h are available.",
                "Pull down anywhere to refresh your stats.",
                "New installs need about 3 days of data before history and trends become reliable."
            )
        )
    )

    private val focus = FeatureInfo(
        title = "Focus",
        summary = "The central hub for building distraction controls: app & website Shields, usage Goals, and blocking Schedules.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Shields block or gate chosen apps and websites whenever you try to open them.",
                "Goals let apps run freely but reward staying under a usage limit with streaks and incentives.",
                "Schedules activate shields automatically at set days and times."
            ),
            FeatureInfoSections.whatYoullSee(
                "Apps/Websites tabs listing Active Goals, Active Shields, and Schedules with sortable headers.",
                "An expanding + button to add a Shield, Goal, or Schedule.",
                "A red banner when editing is restricted by Lockdown hours."
            ),
            FeatureInfoSections.tips(
                "Long-press any item to enter multi-selection mode, then long-press again to toggle more items.",
                "Deleting requires completing the 3-lever confirmation puzzle on purpose — friction helps you reconsider.",
                "Website shielding needs the Accessibility Service enabled."
            )
        )
    )

    private val settings = FeatureInfo(
        title = "Settings",
        summary = "Global preferences and the entry point to every category of Zenith's configuration.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Controls global behavior: screen-time target, emergency recharge, delay-app duration, day start time, and permissions.",
                "Routes into categorized settings: Features, Appearance, Performance, Data Management, and Developer.",
                "Handles app updates and changelog access."
            ),
            FeatureInfoSections.whatYoullSee(
                "A featured carousel surfacing updates and shortcuts.",
                "General settings blocks plus one card per category.",
                "An About section with developer-mode toggle and update options."
            ),
            FeatureInfoSections.tips(
                "Updates are checked automatically on open if enabled; otherwise use the manual check button.",
                "Whitelisted apps and excluded-from-tracking apps are managed via sheets inside General Settings.",
                "Enable developer mode in About to unlock extra categories and tools."
            )
        )
    )

    private val usageStats = FeatureInfo(
        title = "Usage Stats",
        summary = "Deep-dive analytics of your screen time by day, hour, app, website, and focus category — plus personal insights.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Breaks usage down into Shield / Goal / Other categories and shows where your hours actually go.",
                "Detects patterns like peak hours, efficiency score, sessions, and a time profile (e.g. Night Owl)."
            ),
            FeatureInfoSections.whatYoullSee(
                "A donut chart per category, hourly bar chart (bedtime hours tinted), and weekly trend calendar.",
                "Per-hour drill-down lists ('Apps used at HH:00') with Usage/Recent sorting.",
                "Highlight and About You sections with heatmap, snapshot stamps, and weekly stats."
            ),
            FeatureInfoSections.tips(
                "Tap a donut segment to highlight that category across the charts.",
                "Tap any hour bar to expand its app breakdown; tap the Time Profile card to jump to your peak hour.",
                "Pull down to refresh — with Smart Repair enabled it also recalculates inconsistent records."
            )
        )
    )

    private val bedtime = FeatureInfo(
        title = "Bedtime",
        summary = "A nightly mode that keeps you off distracting apps during sleep hours, gamified with streaks.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Blocks non-allowed apps during your bedtime window and counts a streak for every night you respect it.",
                "Optional companions: wind-down notification before bed, Do Not Disturb, and pre-bed restrictions."
            ),
            FeatureInfoSections.whatYoullSee(
                "A large breathing countdown ring showing when bedtime ends or starts, plus weekday picker.",
                "Current/best streak cards, a Bedtime Activity hourly chart, and History/Efficiency cards.",
                "Settings cards for allowed apps, wind-down notification, DND, and restrictions."
            ),
            FeatureInfoSections.tips(
                "Turning bedtime OFF mid-window intentionally requires the hardest confirmation (10 levers in 10 seconds).",
                "Add trusted apps (e.g. music, phone) to Allowed Apps so they bypass blocking.",
                "Tap an activity bar to see exactly which apps were used at that hour."
            )
        )
    )

    private val alarm = FeatureInfo(
        title = "Alarm",
        summary = "A full alarm clock built to get you out of bed, integrated with Zenith's anti-oversleep tooling.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Fires alarms with rich wake-up enforcement: math challenges, spoken phrases, gradual volume, and snooze limits.",
                "Can auto-open selected 'wake up' apps when dismissed so you start your morning routine."
            ),
            FeatureInfoSections.whatYoullSee(
                "Alarm cards grouped by day with name, oversized time, live countdown chip, and repeat/vibrate icons.",
                "A sort control (Time / Name / Closest), a + button to add alarms, and a detailed edit sheet.",
                "Today-active alarms highlight with primary-container color."
            ),
            FeatureInfoSections.tips(
                "Swipe an alarm row left/right for edit and delete actions.",
                "Long-press to enter selection mode for bulk deletion (3-lever puzzle).",
                "Changing an alarm's time or days re-enables it automatically; the master switch in the header gates all alarms."
            )
        )
    )

    private val gracePeriod = FeatureInfo(
        title = "Grace Period",
        summary = "A scheduled daily window in which every Zenith restriction is lifted — making blocking sustainable instead of absolute.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "During the window, shields, schedules, and bedtime will not block any app.",
                "Outside the window, all protections return automatically — no manual re-enabling needed."
            ),
            FeatureInfoSections.whatYoullSee(
                "A toggle card, a pulsing countdown ring ('Grace period ends in…' / 'Starts in…'), and time pickers.",
                "A weekday selector so the window only applies on chosen days.",
                "An info card explaining the override behavior."
            ),
            FeatureInfoSections.tips(
                "Plan a realistic window (e.g. lunch break) rather than toggling impulsively — disabling mid-session requires the 10-lever confirmation.",
                "Wrap-around times are supported, so windows can span midnight.",
                "Combine with Lockdown if you want editing protected while grace period is off."
            )
        )
    )

    private val eyeCare = FeatureInfo(
        title = "Eye Care",
        summary = "Applies the 20-20-20 rule: after a stretch of screen time you get a short, unskippable rest overlay.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Tracks continuous usage and forces a brief rest break so your eyes can recover.",
                "Both the work length and rest length are tunable."
            ),
            FeatureInfoSections.whatYoullSee(
                "A toggle card, a Work Time slider (1–60 min), and a Rest Time slider (5–60 sec).",
                "While active, a full-screen rest overlay appears when work time expires."
            ),
            FeatureInfoSections.tips(
                "Classic guidance is ~20 minutes of work followed by ~20 seconds looking away — both sliders let you adapt it.",
                "Sliders commit on release, so drag freely until the value feels right.",
                "The break overlay is intentionally unskippable — that's the point."
            )
        )
    )

    private val appDetail = FeatureInfo(
        title = "App Detail",
        summary = "Everything about a single app: its usage, streak, limits, and the shield or goal attached to it.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Shows today's usage against this app's limit/goal, yesterday comparison, trend, and streak.",
                "Lets you manage the app's Shield or Goal directly, including pausing it temporarily."
            ),
            FeatureInfoSections.whatYoullSee(
                "Header with the app icon (dimmed when blocked), streak badge, and SHIELD/GOAL type chip.",
                "'Today's Usage' hero card, Yesterday/Trend stat cards, a 21-day history chart, and an hourly chart.",
                "Battery usage, Peak Usage Hour, and Pause/Delete actions when a focus is active."
            ),
            FeatureInfoSections.tips(
                "Use the pencil icon in the top bar to open this app's shield/goal settings sheet.",
                "Pausing asks for a duration via the lever sheet; deleting requires the 3-lever puzzle.",
                "Pull down to refresh the stats."
            )
        )
    )

    private val lockdown = FeatureInfo(
        title = "Lockdown",
        summary = "Schedule hours during which Focus-screen editing is completely blocked — protecting your controls from your future self.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "During lockdown hours you cannot add, edit, or delete shields, goals, or schedules.",
                "Disabling lockdown mid-session deliberately requires the hardest confirmation."
            ),
            FeatureInfoSections.whatYoullSee(
                "A toggle card and a large countdown ring ('Lockdown starts in…' / red 'ends in…' while active).",
                "Start/end time pickers, weekday selector, and a Disable Lockdown button."
            ),
            FeatureInfoSections.tips(
                "Set lockdown for your most impulsive hours (e.g. late night).",
                "Turning it on is easy on purpose; escaping takes 10 levers within 10 seconds.",
                "Combine with Grace Period so breaks stay planned, not improvised."
            )
        )
    )

    private val pomodoro = FeatureInfo(
        title = "Pomodoro",
        summary = "A focus timer that breaks work into sessions separated by short breaks — with optional app lockdown during sessions.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Runs repeating focus/break cycles with configurable lengths, session counts, and long-break cadence.",
                "Optionally blocks distracting apps for the whole session, enforced behind a puzzle."
            ),
            FeatureInfoSections.whatYoullSee(
                "A big wavy circular countdown that changes color between session, break, and paused states.",
                "Configuration cards for durations, sessions, App Protection toggles, and the Allowed Apps picker.",
                "Named presets you can save, load, and delete (e.g. 'Work', 'Study')."
            ),
            FeatureInfoSections.tips(
                "Emptying the allowed-apps selection means every app is blocked during sessions.",
                "Ending a session early requires the 10-lever confirmation — commit before you start.",
                "Try 'Pauseable Session' if you need legit interruptions without losing progress."
            )
        )
    )

    private val pausePoint = FeatureInfo(
        title = "Pause Point",
        summary = "Requires completing a small task before a block overlay lets you through — a speed bump against mindless unblocking.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "When an interception fires, a random enabled task must be completed before continuing.",
                "You choose which task types may appear from the toggles below."
            ),
            FeatureInfoSections.whatYoullSee(
                "Task type cards with individual switches (e.g. typing, QR scan).",
                "For QR scan: how many codes are saved and a shortcut to Manage QR Codes.",
                "The header switch quickly enables/disables the whole feature."
            ),
            FeatureInfoSections.tips(
                "Enable multiple task types so the challenge stays unpredictable.",
                "If you enable QR scanning, register codes first — otherwise the task cannot be satisfied.",
                "Fewer enabled types = faster unblock, more types = stronger friction."
            )
        )
    )

    private val pausePointQr = FeatureInfo(
        title = "QR Codes",
        summary = "Register the physical QR codes accepted by the Pause Point QR-scan task.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Scans and saves QR codes that Pause Point will later demand when you try to unblock an app.",
                "Any saved code satisfies the task — so put them somewhere getting up matters."
            ),
            FeatureInfoSections.whatYoullSee(
                "A live camera scanner preview (with permission fallback) and a 'Code saved!' banner.",
                "A Saved Codes list where each entry can be copied or deleted."
            ),
            FeatureInfoSections.tips(
                "Print codes and stick them far from your desk — fridge, another room — to add real friction.",
                "Keep at least one spare code registered in case one gets lost.",
                "Codes are stored locally; delete ones you no longer use."
            )
        )
    )

    private val overlayAppearance = FeatureInfo(
        title = "Overlay Appearance",
        summary = "Customize how blocking and interception overlays look, with a live preview of the real thing.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Controls the color theme, transparency, and layout of the bottom-sheet overlays shown when something is blocked."
            ),
            FeatureInfoSections.whatYoullSee(
                "A live Preview card rendering the actual overlay for 'Example App'.",
                "Color Theme swatches (including Dynamic and Custom), an Opacity slider, and a Full-Screen toggle."
            ),
            FeatureInfoSections.tips(
                "Watch the preview while dragging the opacity slider to find a level that's visible but not blinding.",
                "Custom unlocks a hue slider for a fully personal palette.",
                "Full-screen makes the sheet fill the display — useful if small sheets tempt you to peek around them."
            )
        )
    )

    private val gsFlexCustomizer = FeatureInfo(
        title = "GS Flex Designer",
        summary = "Live-tune Zenith's variable Google Sans Flex typography, style by style.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Adjusts font axes (weight, width, etc.) per text style and previews the result instantly."
            ),
            FeatureInfoSections.whatYoullSee(
                "A preview card with wordmark, headline, and body samples.",
                "Preset chips (Zenith / Neo / Impact / Airy) and a custom switch unlocking fine-grained axis sliders."
            ),
            FeatureInfoSections.tips(
                "Start from a preset, then flip 'Use Custom Variable' to fine-tune individual axes.",
                "Use copy/save to keep configurations you like before experimenting further."
            )
        )
    )

    private val featuresCategory = FeatureInfo(
        title = "Features",
        summary = "Toggle Zenith's behavioral modules: interface overlays, notifications, entry control, triggers, and tracking.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Interface Overlays: total usage pill, session HUD, usage glimpse, and current calendar event.",
                "Entry Control: Mindful Gateway pauses before opening apps; Incentive Lock unlocks shields progressively as goals complete.",
                "Triggers: pause media on overlay, Early Kick before limit, battery reset tracking; plus website auto-tracking."
            ),
            FeatureInfoSections.whatYoullSee(
                "Grouped sections of switches and sub-settings, with links to Grace Period, Eye Care, Pomodoro, Lockdown, and Pause Point."
            ),
            FeatureInfoSections.tips(
                "Incentive Lock gives bonus uses at 25%/50% goal completion; disabling it starts a 1-hour countdown you can still cancel.",
                "Early Kick ejects you from an app 5 minutes before its limit — great as a warning system."
            )
        )
    )

    private val appearanceCategory = FeatureInfo(
        title = "Appearance",
        summary = "Make Zenith yours: theme, fonts, dynamic color, expressive colors, and navigation layout.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Chooses light/dark theme, app font (including the GS Flex customizer), Dynamic Color on Android 12+, and the expressive color set.",
                "Layout options such as the floating tab bar and overlay appearance live here too."
            ),
            FeatureInfoSections.whatYoullSee(
                "Theme/font selectors, toggles for Dynamic Color and Expressive Colors, and layout switches."
            ),
            FeatureInfoSections.tips(
                "Dynamic Color follows your wallpaper; Expressive Colors adds livelier accents on top.",
                "Open Overlay Appearance from here to style the blocking sheets themselves."
            )
        )
    )

    private val performanceCategory = FeatureInfo(
        title = "Performance",
        summary = "Balance responsiveness vs battery: preset profiles, overnight monitoring pauses, and detection tuning.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Performance Profile sets how aggressively Zenith detects app opens (higher = snappier, more battery).",
                "Unused Hours pauses monitoring overnight; Instant Detection uses the Accessibility Service for immediate checks.",
                "Custom Tuning exposes polling-delay sliders per context."
            ),
            FeatureInfoSections.whatYoullSee(
                "Selectable profile circles, unused-hours time pickers, and threshold sliders with reset buttons."
            ),
            FeatureInfoSections.tips(
                "Changes here must be applied via the floating Apply Settings button before leaving — Back is intercepted until then.",
                "If detection feels slow, raise the profile first before touching custom sliders.",
                "Enabling Instant Detection improves accuracy but asks for the accessibility service."
            )
        )
    )

    private val dataManagementCategory = FeatureInfo(
        title = "Data Management",
        summary = "Back up and restore your entire Zenith setup so nothing is lost between devices or reinstalls.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Auto Backup saves periodic snapshots (3–24h interval) to a folder you choose.",
                "Manual Backup exports on demand; Restore brings everything back and restarts the app."
            ),
            FeatureInfoSections.whatYoullSee(
                "Backup & Restore card with last-backup time, interval chips, and action buttons.",
                "Sync & Maintenance options like Sync on Entry."
            ),
            FeatureInfoSections.tips(
                "Pick a cloud-synced folder (e.g. Drive) as the auto-backup location for off-device safety.",
                "Restore shows a metadata confirmation sheet first — verify the date before proceeding."
            )
        )
    )

    private val developerCategory = FeatureInfo(
        title = "Developer",
        summary = "Power tools: database editor, repair utilities, live logs, onboarding triggers, and UI test harnesses.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Edits raw records (streaks, global stats), inspects the database, and runs Smart Repair.",
                "Re-runs onboarding flows and provides functional test surfaces (overlays, TTS, fonts)."
            ),
            FeatureInfoSections.whatYoullSee(
                "Database Editor, Database Records viewer, System Usage Fetch, Data Repairment entries.",
                "Live DB and Overlay log viewers plus various test launchers."
            ),
            FeatureInfoSections.tips(
                "Prefer Smart Repair over manual edits unless you know exactly what you're changing.",
                "Log viewers are invaluable when reporting bugs — attach their output."
            )
        )
    )

    private val databaseDebug = FeatureInfo(
        title = "Database Records",
        summary = "Raw view of every recorded daily usage row stored by Zenith.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Lists all daily usage records so you can inspect exactly what the tracker has collected."
            ),
            FeatureInfoSections.whatYoullSee(
                "The record list (with an analysing loader) and a Manage Carryover FAB for reviewing/deleting hourly carryover entries grouped by hour."
            ),
            FeatureInfoSections.tips(
                "Use this read-only inspector to sanity-check numbers seen on charts.",
                "Delete carryover entries only if you understand they affect recalculated history."
            )
        )
    )

    private val dataRepairment = FeatureInfo(
        title = "Data Repairment",
        summary = "Detects and fixes missing or inconsistent days in your usage history.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Scans history for gaps or wrong values and walks you through repairing selected days.",
                "Shows a success state when everything already looks good."
            ),
            FeatureInfoSections.whatYoullSee(
                "Either an 'All data looks good!' message or a list of repairable days.",
                "An Advanced Repair Mode switch revealing today and days that already have records."
            ),
            FeatureInfoSections.tips(
                "Run this after reinstalling the app or changing system clock settings.",
                "Advanced mode can overwrite existing records — use it only when values look clearly wrong."
            )
        )
    )

    private val systemUsageDebug = FeatureInfo(
        title = "System Usage Fetch",
        summary = "Diagnostic ground truth pulled straight from Android's UsageStatsManager using Digital Wellbeing logic.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Fetches usage events directly from the OS to compare against Zenith's own database."
            ),
            FeatureInfoSections.whatYoullSee(
                "A 'System Usage (21 Days)' chart with explanatory text about the calculation method."
            ),
            FeatureInfoSections.tips(
                "Read-only by design — nothing here modifies your data.",
                "Use it to verify whether a mismatch comes from Android itself or from Zenith's storage."
            )
        )
    )

    private val fontTest = FeatureInfo(
        title = "M3 Expressive Editor",
        summary = "Variable-font playground for testing type scales, presets, and axis values.",
        sections = listOf(
            FeatureInfoSections.whatItDoes(
                "Renders sample text and specimens at display size so typography changes can be evaluated quickly."
            ),
            FeatureInfoSections.whatYoullSee(
                "Tabs for Custom text, Scale specimens, and Presets, plus sliders for global font axes."
            ),
            FeatureInfoSections.tips(
                "Type anything in Custom to stress-test glyphs at large sizes.",
                "Presets are a fast way to A/B different axis combinations."
            )
        )
    )
}
