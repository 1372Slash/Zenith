package com.etrisad.zenith.data.model

object AppCategoryDefaults {

    private val exact: Map<String, AppCategory> = mapOf(
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.facebook.orca" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.snapchat.android" to AppCategory.SOCIAL,
        "com.whatsapp" to AppCategory.SOCIAL,
        "com.whatsapp.w4b" to AppCategory.SOCIAL,
        "org.telegram.messenger" to AppCategory.SOCIAL,
        "org.telegram.messenger.web" to AppCategory.SOCIAL,
        "com.discord" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        "com.linkedin.android" to AppCategory.SOCIAL,
        "com.pinterest" to AppCategory.SOCIAL,
        "com.google.android.apps.messaging" to AppCategory.SOCIAL,
        "com.google.android.talk" to AppCategory.SOCIAL,
        "com.viber.voip" to AppCategory.SOCIAL,
        "jp.naver.line.android" to AppCategory.SOCIAL,
        "com.kakao.talk" to AppCategory.SOCIAL,
        "com.google.android.youtube" to AppCategory.LEISURE,
        "com.google.android.apps.youtube.music" to AppCategory.LEISURE,
        "com.netflix.mediaclient" to AppCategory.LEISURE,
        "com.spotify.music" to AppCategory.LEISURE,
        "com.twitch.android.app" to AppCategory.LEISURE,
        "com.disney.disneyplus" to AppCategory.LEISURE,
        "com.amazon.avod.thirdpartyclient" to AppCategory.LEISURE,
        "com.zhiliaoapp.musically" to AppCategory.LEISURE,
        "com.ss.android.ugc.trill" to AppCategory.LEISURE,
        "com.roblox.client" to AppCategory.LEISURE,
        "com.mojang.minecraftpe" to AppCategory.LEISURE,
        "com.tencent.ig" to AppCategory.LEISURE,
        "com.mobile.legends" to AppCategory.LEISURE,
        "com.garena.game.kgid" to AppCategory.LEISURE,
        "com.miHoYo.GenshinImpact" to AppCategory.LEISURE,
        "com.epicgames.fortnite" to AppCategory.LEISURE,
        "com.supercell.clashofclans" to AppCategory.LEISURE,
        "com.supercell.clashroyale" to AppCategory.LEISURE,
        "com.innersloth.spacemafia" to AppCategory.LEISURE,
        "com.king.candycrushsaga" to AppCategory.LEISURE,
        "com.google.android.apps.docs" to AppCategory.PRODUCTIVE,
        "com.google.android.apps.docs.editors.docs" to AppCategory.PRODUCTIVE,
        "com.google.android.apps.docs.editors.sheets" to AppCategory.PRODUCTIVE,
        "com.google.android.apps.docs.editors.slides" to AppCategory.PRODUCTIVE,
        "com.google.android.calendar" to AppCategory.PRODUCTIVE,
        "com.google.android.apps.classroom" to AppCategory.PRODUCTIVE,
        "com.google.android.keep" to AppCategory.PRODUCTIVE,
        "com.notion.id" to AppCategory.PRODUCTIVE,
        "com.slack" to AppCategory.PRODUCTIVE,
        "com.microsoft.teams" to AppCategory.PRODUCTIVE,
        "us.zoom.videomeetings" to AppCategory.PRODUCTIVE,
        "com.microsoft.office.officehubrow" to AppCategory.PRODUCTIVE,
        "com.microsoft.office.word" to AppCategory.PRODUCTIVE,
        "com.microsoft.office.excel" to AppCategory.PRODUCTIVE,
        "com.microsoft.office.powerpoint" to AppCategory.PRODUCTIVE,
        "com.github.android" to AppCategory.PRODUCTIVE,
        "com.openai.chatgpt" to AppCategory.PRODUCTIVE,
        "com.duolingo" to AppCategory.PRODUCTIVE,
        "com.coursera.android" to AppCategory.PRODUCTIVE,
        "com.udemy.android" to AppCategory.PRODUCTIVE,
        "com.ichi2.anki" to AppCategory.PRODUCTIVE,
        "com.todoist" to AppCategory.PRODUCTIVE,
        "com.trello" to AppCategory.PRODUCTIVE,
        "com.android.chrome" to AppCategory.UTILITIES,
        "com.chrome.beta" to AppCategory.UTILITIES,
        "org.mozilla.firefox" to AppCategory.UTILITIES,
        "com.brave.browser" to AppCategory.UTILITIES,
        "com.opera.browser" to AppCategory.UTILITIES,
        "com.microsoft.emmx" to AppCategory.UTILITIES,
        "com.sec.android.app.sbrowser" to AppCategory.UTILITIES,
        "com.android.camera" to AppCategory.UTILITIES,
        "com.google.android.GoogleCamera" to AppCategory.UTILITIES,
        "com.google.android.apps.photos" to AppCategory.UTILITIES,
        "com.google.android.deskclock" to AppCategory.UTILITIES,
        "com.google.android.calculator" to AppCategory.UTILITIES,
        "com.google.android.dialer" to AppCategory.UTILITIES,
        "com.google.android.apps.maps" to AppCategory.UTILITIES,
        "com.google.android.apps.walletnfcrel" to AppCategory.UTILITIES,
        "com.google.android.apps.nbu.files" to AppCategory.UTILITIES,
        "com.google.android.documentsui" to AppCategory.UTILITIES,
        "com.android.settings" to AppCategory.UTILITIES,
        "com.android.vending" to AppCategory.UTILITIES,
        "com.google.android.gm" to AppCategory.UTILITIES,
        "com.google.android.apps.tachyon" to AppCategory.UTILITIES
    )

    private val productiveKeywords = listOf(
        "notion", "docs", "sheets", "slides", "calendar", "classroom", "keep notes",
        "slack", "teams", "zoom", "office", "word", "excel", "powerpoint", "gpt",
        "duolingo", "coursera", "udemy", "learn", "course", "study", "todo", "task",
        "github", "gitlab", "trello", "jira", "figma", "anki", "quizlet", "canvas"
    )

    private val socialKeywords = listOf(
        "instagram", "facebook", "messenger", "whatsapp", "telegram", "twitter",
        "discord", "snapchat", "reddit", "linkedin", "pinterest", "social",
        "messaging", "chat", "forum", "community"
    )

    private val leisureKeywords = listOf(
        "youtube", "netflix", "spotify", "twitch", "disney", "prime video",
        "hulu", "music", "video", "player", "stream", "game", "games", "gaming",
        "tiktok", "musically", "comic", "manga", "anime", "movie", "tv",
        "candy crush", "roblox", "minecraft", "pubg", "fortnite"
    )

    private val utilityKeywords = listOf(
        "bank", "banking", "wallet", "bca", "mandiri", "bri", "bni", "gopay",
        "ovo", "dana", "camera", "gallery", "calculator", "clock", "alarm",
        "maps", "files", "settings", "browser", "store", "launcher", "dialer",
        "phone", "keyboard", "weather", "scanner", "vpn"
    )

    fun forPackage(packageName: String, appName: String? = null): AppCategory? {
        exact[packageName]?.let { return it }
        val pkg = packageName.lowercase()
        if (pkg.startsWith("zenith-web:")) {
            return forWebsite(pkg.removePrefix("zenith-web:"))
        }
        val haystack = "$pkg ${appName.orEmpty().lowercase()}"
        return when {
            utilityKeywords.any { haystack.contains(it) } -> AppCategory.UTILITIES
            productiveKeywords.any { haystack.contains(it) } -> AppCategory.PRODUCTIVE
            socialKeywords.any { haystack.contains(it) } -> AppCategory.SOCIAL
            leisureKeywords.any { haystack.contains(it) } -> AppCategory.LEISURE
            else -> null
        }
    }

    private val websiteExact: Map<String, AppCategory> = mapOf(
        "github.com" to AppCategory.PRODUCTIVE,
        "gitlab.com" to AppCategory.PRODUCTIVE,
        "bitbucket.org" to AppCategory.PRODUCTIVE,
        "stackoverflow.com" to AppCategory.PRODUCTIVE,
        "stackexchange.com" to AppCategory.PRODUCTIVE,
        "developer.android.com" to AppCategory.PRODUCTIVE,
        "developer.mozilla.org" to AppCategory.PRODUCTIVE,
        "docs.google.com" to AppCategory.PRODUCTIVE,
        "drive.google.com" to AppCategory.PRODUCTIVE,
        "calendar.google.com" to AppCategory.PRODUCTIVE,
        "classroom.google.com" to AppCategory.PRODUCTIVE,
        "notion.so" to AppCategory.PRODUCTIVE,
        "notion.site" to AppCategory.PRODUCTIVE,
        "figma.com" to AppCategory.PRODUCTIVE,
        "slack.com" to AppCategory.PRODUCTIVE,
        "trello.com" to AppCategory.PRODUCTIVE,
        "asana.com" to AppCategory.PRODUCTIVE,
        "linear.app" to AppCategory.PRODUCTIVE,
        "atlassian.net" to AppCategory.PRODUCTIVE,
        "coursera.org" to AppCategory.PRODUCTIVE,
        "udemy.com" to AppCategory.PRODUCTIVE,
        "khanacademy.org" to AppCategory.PRODUCTIVE,
        "duolingo.com" to AppCategory.PRODUCTIVE,
        "quizlet.com" to AppCategory.PRODUCTIVE,
        "chatgpt.com" to AppCategory.PRODUCTIVE,
        "chat.openai.com" to AppCategory.PRODUCTIVE,
        "claude.ai" to AppCategory.PRODUCTIVE,
        "gemini.google.com" to AppCategory.PRODUCTIVE,
        "perplexity.ai" to AppCategory.PRODUCTIVE,
        "wikipedia.org" to AppCategory.PRODUCTIVE,
        "medium.com" to AppCategory.PRODUCTIVE,
        "canva.com" to AppCategory.PRODUCTIVE,
        "zoom.us" to AppCategory.PRODUCTIVE,
        "meet.google.com" to AppCategory.PRODUCTIVE,
        "teams.microsoft.com" to AppCategory.PRODUCTIVE,
        "arxiv.org" to AppCategory.PRODUCTIVE,
        "scholar.google.com" to AppCategory.PRODUCTIVE,
        "instagram.com" to AppCategory.SOCIAL,
        "facebook.com" to AppCategory.SOCIAL,
        "x.com" to AppCategory.SOCIAL,
        "twitter.com" to AppCategory.SOCIAL,
        "reddit.com" to AppCategory.SOCIAL,
        "linkedin.com" to AppCategory.SOCIAL,
        "pinterest.com" to AppCategory.SOCIAL,
        "snapchat.com" to AppCategory.SOCIAL,
        "threads.net" to AppCategory.SOCIAL,
        "discord.com" to AppCategory.SOCIAL,
        "whatsapp.com" to AppCategory.SOCIAL,
        "telegram.org" to AppCategory.SOCIAL,
        "t.me" to AppCategory.SOCIAL,
        "quora.com" to AppCategory.SOCIAL,
        "9gag.com" to AppCategory.SOCIAL,
        "tumblr.com" to AppCategory.SOCIAL,
        "youtube.com" to AppCategory.LEISURE,
        "youtu.be" to AppCategory.LEISURE,
        "netflix.com" to AppCategory.LEISURE,
        "twitch.tv" to AppCategory.LEISURE,
        "spotify.com" to AppCategory.LEISURE,
        "disneyplus.com" to AppCategory.LEISURE,
        "primevideo.com" to AppCategory.LEISURE,
        "hulu.com" to AppCategory.LEISURE,
        "max.com" to AppCategory.LEISURE,
        "crunchyroll.com" to AppCategory.LEISURE,
        "bilibili.com" to AppCategory.LEISURE,
        "vimeo.com" to AppCategory.LEISURE,
        "dailymotion.com" to AppCategory.LEISURE,
        "steamcommunity.com" to AppCategory.LEISURE,
        "roblox.com" to AppCategory.LEISURE,
        "chess.com" to AppCategory.LEISURE,
        "imdb.com" to AppCategory.LEISURE,
        "letterboxd.com" to AppCategory.LEISURE,
        "tiktok.com" to AppCategory.LEISURE,
        "shopee.co.id" to AppCategory.LEISURE,
        "tokopedia.com" to AppCategory.LEISURE,
        "lazada.co.id" to AppCategory.LEISURE,
        "blibli.com" to AppCategory.LEISURE,
        "bukalapak.com" to AppCategory.LEISURE,
        "amazon.com" to AppCategory.LEISURE,
        "aliexpress.com" to AppCategory.LEISURE,
        "detik.com" to AppCategory.LEISURE,
        "kompas.com" to AppCategory.LEISURE,
        "mail.google.com" to AppCategory.UTILITIES,
        "gmail.com" to AppCategory.UTILITIES,
        "outlook.com" to AppCategory.UTILITIES,
        "outlook.office.com" to AppCategory.UTILITIES,
        "proton.me" to AppCategory.UTILITIES,
        "paypal.com" to AppCategory.UTILITIES,
        "wise.com" to AppCategory.UTILITIES,
        "maps.google.com" to AppCategory.UTILITIES,
        "speedtest.net" to AppCategory.UTILITIES
    )

    private val websiteProductiveKeywords = listOf(
        "docs", "notion", "figma", "slack", "atlassian", "jira", "trello", "coursera",
        "udemy", "khanacademy", "duolingo", "quizlet", "chatgpt", "openai", "claude",
        "gemini", "perplexity", "wikipedia", "scholar", "arxiv", "medium", "canva",
        "classroom", "learn", "course", "study", "tutorial", "developer", "documentation",
        "github", "gitlab", "stackoverflow"
    )

    private val websiteSocialKeywords = listOf(
        "instagram", "facebook", "twitter", "reddit", "linkedin", "pinterest",
        "snapchat", "threads", "discord", "whatsapp", "telegram", "quora", "forum",
        "community", "social"
    )

    private val websiteLeisureKeywords = listOf(
        "youtube", "youtu.be", "netflix", "twitch", "spotify", "disney", "primevideo",
        "hulu", "crunchyroll", "bilibili", "vimeo", "dailymotion", "steam", "roblox",
        "chess", "imdb", "letterboxd", "tiktok", "music", "video", "movie", "stream",
        "shopee", "tokopedia", "lazada", "blibli", "bukalapak", "amazon", "aliexpress",
        "game", "games", "news", "detik", "kompas"
    )

    private val websiteUtilityKeywords = listOf(
        "mail", "gmail", "outlook", "paypal", "wallet", "bank", "maps", "weather",
        "vpn", "speedtest"
    )

    private fun forWebsite(rawDomain: String): AppCategory? {
        val host = rawDomain.removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
        websiteExact[host]?.let { return it }
        val parts = host.split('.')
        for (index in 1 until parts.size) {
            websiteExact[parts.drop(index).joinToString(".")]?.let { return it }
        }
        return when {
            websiteProductiveKeywords.any { host.contains(it) } -> AppCategory.PRODUCTIVE
            websiteSocialKeywords.any { host.contains(it) } -> AppCategory.SOCIAL
            websiteLeisureKeywords.any { host.contains(it) } -> AppCategory.LEISURE
            websiteUtilityKeywords.any { host.contains(it) } -> AppCategory.UTILITIES
            else -> null
        }
    }
}
