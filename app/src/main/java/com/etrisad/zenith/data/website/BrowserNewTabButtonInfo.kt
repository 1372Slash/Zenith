package com.etrisad.zenith.data.website

data class BrowserNewTabButtonInfo(
    val tabSwitcherId: String? = null,
    val newTabButtonId: String? = null,
    val fallbackContentDescriptions: List<String> = emptyList(),
)

val NEW_TAB_BUTTON_ID_LIST = mapOf(
    "com.android.chrome" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.android.chrome:id/tab_switcher_button",
        newTabButtonId = "com.android.chrome:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "com.chrome.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.chrome.beta:id/tab_switcher_button",
        newTabButtonId = "com.chrome.beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "com.chrome.dev" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.chrome.dev:id/tab_switcher_button",
        newTabButtonId = "com.chrome.dev:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "com.chrome.canary" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.chrome.canary:id/tab_switcher_button",
        newTabButtonId = "com.chrome.canary:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "org.chromium.chrome" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.chromium.chrome:id/tab_switcher_button",
        newTabButtonId = "org.chromium.chrome:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "com.android.chrome.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.android.chrome.beta:id/tab_switcher_button",
        newTabButtonId = "com.android.chrome.beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Switch tabs", "Tabs")
    ),
    "com.sec.android.app.sbrowser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.sec.android.app.sbrowser:id/tabs_switcher_button",
        newTabButtonId = "com.sec.android.app.sbrowser:id/new_tab_image_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.sec.android.app.sbrowser.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.sec.android.app.sbrowser.beta:id/tabs_switcher_button",
        newTabButtonId = "com.sec.android.app.sbrowser.beta:id/new_tab_image_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.mozilla.firefox" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.mozilla.firefox:id/tabs_tray",
        newTabButtonId = "org.mozilla.firefox:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.mozilla.firefox_beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.mozilla.firefox_beta:id/tabs_tray",
        newTabButtonId = "org.mozilla.firefox_beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.mozilla.fenix" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.mozilla.fenix:id/tabs_tray",
        newTabButtonId = "org.mozilla.fenix:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.mozilla.focus" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.mozilla.focus:id/tabs_tray",
        newTabButtonId = "org.mozilla.focus:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.mozilla.fennec_fdroid" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.mozilla.fennec_fdroid:id/tabs_tray",
        newTabButtonId = "org.mozilla.fennec_fdroid:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.ironfoxoss.ironfox" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.ironfoxoss.ironfox:id/tabs_tray",
        newTabButtonId = "org.ironfoxoss.ironfox:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "io.github.forkmaintainers.iceraven" to BrowserNewTabButtonInfo(
        tabSwitcherId = "io.github.forkmaintainers.iceraven:id/tabs_tray",
        newTabButtonId = "io.github.forkmaintainers.iceraven:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.microsoft.emmx" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.microsoft.emmx:id/tab_switcher_button",
        newTabButtonId = "com.microsoft.emmx:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.microsoft.emmx.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.microsoft.emmx.beta:id/tab_switcher_button",
        newTabButtonId = "com.microsoft.emmx.beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.microsoft.edge.canary" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.microsoft.edge.canary:id/tab_switcher_button",
        newTabButtonId = "com.microsoft.edge.canary:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.microsoft.edge.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.microsoft.edge.beta:id/tab_switcher_button",
        newTabButtonId = "com.microsoft.edge.beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.brave.browser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.brave.browser:id/tab_switcher_button",
        newTabButtonId = "com.brave.browser:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.opera.browser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.opera.browser:id/tab_switcher_button",
        newTabButtonId = "com.opera.browser:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.opera.mini.native" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.opera.mini.native:id/tab_switcher_button",
        newTabButtonId = "com.opera.mini.native:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.opera.browser.beta" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.opera.browser.beta:id/tab_switcher_button",
        newTabButtonId = "com.opera.browser.beta:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.duckduckgo.mobile.android" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.duckduckgo.mobile.android:id/tab_switcher_button",
        newTabButtonId = "com.duckduckgo.mobile.android:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.vivaldi.browser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.vivaldi.browser:id/tab_switcher_button",
        newTabButtonId = "com.vivaldi.browser:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "com.kiwibrowser.browser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "com.kiwibrowser.browser:id/tab_switcher_button",
        newTabButtonId = "com.kiwibrowser.browser:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "mark.via.gp" to BrowserNewTabButtonInfo(
        tabSwitcherId = "mark.via.gp:id/tab_switcher_button",
        newTabButtonId = "mark.via.gp:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "org.cromite.cromite" to BrowserNewTabButtonInfo(
        tabSwitcherId = "org.cromite.cromite:id/tab_switcher_button",
        newTabButtonId = "org.cromite.cromite:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
    "app.vanadium.browser" to BrowserNewTabButtonInfo(
        tabSwitcherId = "app.vanadium.browser:id/tab_switcher_button",
        newTabButtonId = "app.vanadium.browser:id/new_tab_button",
        fallbackContentDescriptions = listOf("New tab", "Tabs", "Switch tabs")
    ),
)
