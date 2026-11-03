/*
 * Adult website preset for Zenith's website blocker.
 *
 * Curbox ships a keyword-matching engine but no bundled adult domain list, so
 * this preset is authored for Zenith using the same pattern syntax:
 *   - explicit brand domains (subdomains are covered by the matcher)
 *   - adult-only TLDs
 *   - tightly scoped brand wildcards, avoiding generic words that would flag
 *     innocent sites.
 */
package com.etrisad.zenith.data.website

object AdultWebsitePreset {

    val KEYWORDS: List<String> = listOf(
        // Adult-only TLDs. The regex requires the adjective to be the final label.
        "r:\\.(?:xxx|porn|adult|sex|sexy)(?=$|[/?#])",

        // Explicitly adult domain labels, e.g. porn.example.com or xxx.net.
        "r:(?:^|\\.)(?:porn|porno|xxx|hentai|nsfw)(?=$|[/?#])",

        // Major explicit video / image platforms.
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "xhamster.desi",
        "redtube.com",
        "youporn.com",
        "spankbang.com",
        "beeg.com",
        "txxx.com",
        "hqporner.com",
        "eporner.com",
        "porntrex.com",
        "motherless.com",
        "rule34.xxx",
        "nhentai.net",
        "hanime.tv",
        "javhd.com",
        "javbus.com",
        "missav.com",
        "jav.guru",
        "sextb.net",

        // Live cams and subscription platforms.
        "chaturbate.com",
        "stripchat.com",
        "livejasmin.com",
        "bongacams.com",
        "cam4.com",
        "flirt4free.com",
        "onlyfans.com",
        "fansly.com",
        "manyvids.com",

        // Targeted brand wildcards catch mirror and regional domains without
        // matching unrelated pages that merely mention a brand in prose.
        "*pornhub*",
        "*xvideos*",
        "*xnxx*",
        "*xhamster*",
        "*redtube*",
        "*youporn*",
        "*spankbang*",
        "*chaturbate*",
        "*stripchat*",
        "*livejasmin*",
        "*bongacams*",
        "*onlyfans*",
        "*fansly*",
        "*nhentai*",
        "*hanime*"
    )
}
