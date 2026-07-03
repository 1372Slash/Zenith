package com.etrisad.zenith.data.website

data class BrowserUrlBarInfo(
    val displayUrlBarId: String,
)

val URL_BAR_ID_LIST = mapOf(
    "com.android.chrome" to BrowserUrlBarInfo("com.android.chrome:id/url_bar"),
    "com.chrome.beta" to BrowserUrlBarInfo("com.chrome.beta:id/url_bar"),
    "com.chrome.dev" to BrowserUrlBarInfo("com.chrome.dev:id/url_bar"),
    "com.chrome.canary" to BrowserUrlBarInfo("com.chrome.canary:id/url_bar"),
    "org.chromium.chrome" to BrowserUrlBarInfo("org.chromium.chrome:id/url_bar"),
    "com.android.chrome.beta" to BrowserUrlBarInfo("com.android.chrome.beta:id/url_bar"),
    "com.sec.android.app.sbrowser" to BrowserUrlBarInfo("com.sec.android.app.sbrowser:id/url_bar"),
    "com.sec.android.app.sbrowser.beta" to BrowserUrlBarInfo("com.sec.android.app.sbrowser.beta:id/url_bar"),
    "org.mozilla.firefox" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "org.mozilla.firefox_beta" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "org.mozilla.fenix" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "org.mozilla.focus" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "org.mozilla.fennec_fdroid" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "org.ironfoxoss.ironfox" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "io.github.forkmaintainers.iceraven" to BrowserUrlBarInfo("ADDRESSBAR_URL_BOX"),
    "com.microsoft.emmx" to BrowserUrlBarInfo("com.microsoft.emmx:id/url_bar"),
    "com.microsoft.emmx.beta" to BrowserUrlBarInfo("com.microsoft.emmx.beta:id/url_bar"),
    "com.microsoft.edge.canary" to BrowserUrlBarInfo("com.microsoft.edge.canary:id/url_bar"),
    "com.microsoft.edge.beta" to BrowserUrlBarInfo("com.microsoft.edge.beta:id/url_bar"),
    "com.brave.browser" to BrowserUrlBarInfo("com.brave.browser:id/url_bar"),
    "com.opera.browser" to BrowserUrlBarInfo("com.opera.browser:id/url_field"),
    "com.opera.mini.native" to BrowserUrlBarInfo("com.opera.mini.native:id/url_field"),
    "com.opera.browser.beta" to BrowserUrlBarInfo("com.opera.browser.beta:id/url_field"),
    "com.duckduckgo.mobile.android" to BrowserUrlBarInfo("com.duckduckgo.mobile.android:id/omniboxTextInput"),
    "com.vivaldi.browser" to BrowserUrlBarInfo("com.vivaldi.browser:id/url_bar"),
    "com.kiwibrowser.browser" to BrowserUrlBarInfo("com.kiwibrowser.browser:id/url_bar"),
    "mark.via.gp" to BrowserUrlBarInfo("mark.via.gp:id/url_bar"),
    "org.cromite.cromite" to BrowserUrlBarInfo("org.cromite.cromite:id/url_bar"),
    "app.vanadium.browser" to BrowserUrlBarInfo("app.vanadium.browser:id/url_bar"),
)
