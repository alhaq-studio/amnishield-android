package com.alhaq.amnishield.data.blockers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

data class PreloadedPack(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val items: List<String>,
    val assetPath: String? = null,
    val bundledDomainCount: Int = items.size
)

object PreloadedPacks {
    /**
     * Built-in domain blocklists matching assets/blocklists/adult_domains.txt (51 domains)
     * and assets/blocklists/haram_domains.txt (40 domains).
     */
    val adultAssetDomains: List<String> = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "onlyfans.com",
        "xhamster.com", "stripchat.com", "redtube.com", "chaturbate.com",
        "youporn.com", "spankbang.com", "tube8.com", "beeg.com",
        "brazzers.com", "cam4.com", "eporner.com", "hdporno.com",
        "livejasmin.com", "naughtyamerica.com", "rule34.xxx", "tnaflix.com",
        "xvideos2.com", "youjizz.com", "bongacams.com", "camsoda.com",
        "faphouse.com", "hentaihaven.xxx", "hqporner.com", "myfreecams.com",
        "pornhubpremium.com", "porntrex.com", "vporn.com", "xhamsterlive.com",
        "xtube.com", "cams.com", "flirt4free.com", "xhamster.one",
        "pornmd.com", "heavy-r.com", "empflix.com", "motherless.com",
        "thumbzilla.com", "drtuber.com", "sunporno.com", "txxx.com",
        "vavgol.com", "fuq.com", "nuvid.com", "gotporn.com",
        "hclips.com", "sexvid.boolean.org", "tubev.sex"
    )

    val gamblingAssetDomains: List<String> = listOf(
        "stake.com", "bet365.com", "bwin.com", "pokerstars.com",
        "roobet.com", "draftkings.com", "fanduel.com", "888casino.com",
        "betfair.com", "1xbet.com", "22bet.com", "unibet.com",
        "betway.com", "williamhill.com", "paddypower.com", "ladbrokes.com",
        "betonline.ag", "bovada.lv", "7bitcasino.com", "bc.game",
        "bitstarz.com", "cloudbet.com", "fortunejack.com", "mbitcasino.com",
        "slotocash.im", "superbook.com", "dundeeslots.com", "ignitioncasino.eu",
        "wildcasino.ag", "spinaway.com", "jackpotcitycasino.com", "spin-casino.com",
        "rubyfortune.com", "royalvegascasino.com", "mummysgold.com", "riverbellecasino.com",
        "allslots-casino.com", "lucky-nugget.com", "gaming-club.com", "mumtazbet.com"
    )

    val keywordPacks = listOf(
        PreloadedPack(
            id = "kw_adult",
            title = "Adult & Explicit Keywords",
            description = "${KeywordPacks.adultKeywords.size} curated adult search terms (porn, nsfw, hentai, etc.)",
            icon = Icons.Outlined.Shield,
            items = KeywordPacks.adultKeywords.toList()
        ),
        PreloadedPack(
            id = "kw_social",
            title = "Social Media & Short Videos",
            description = "12 keywords targeting reels, shorts, and social feeds",
            icon = Icons.Outlined.Devices,
            items = listOf(
                "instagram", "tiktok", "facebook", "twitter", "x.com",
                "reddit", "snapchat", "reels", "shorts", "tumblr", "pinterest", "threads"
            )
        ),
        PreloadedPack(
            id = "kw_gambling",
            title = "Gambling & Betting",
            description = "11 keywords for casino, betting, slots, and lotteries",
            icon = Icons.Outlined.Casino,
            items = listOf(
                "casino", "betting", "poker", "gambling", "slots",
                "stake", "bet365", "lottery", "roulette", "jackpot", "betfair"
            )
        ),
        PreloadedPack(
            id = "kw_gaming",
            title = "Gaming & Live Streams",
            description = "7 keywords for gaming feeds, streams, and Discord",
            icon = Icons.Outlined.SportsEsports,
            items = listOf(
                "twitch", "steam", "roblox", "minecraft", "kick.com", "discord", "fortnite"
            )
        )
    )

    val websitePacks = listOf(
        PreloadedPack(
            id = "web_adult_blocklist",
            title = "Adult Domains Blocklist",
            description = "17,547 verified OISD NSFW domains (compressed offline pack)",
            icon = Icons.Outlined.Shield,
            items = adultAssetDomains,
            assetPath = "blocklists/adult_domains.gz",
            bundledDomainCount = 17547
        ),
        PreloadedPack(
            id = "web_gambling_blocklist",
            title = "Gambling & Betting Blocklist",
            description = "278,862 online casinos, sportsbooks & betting domains (compressed offline pack)",
            icon = Icons.Outlined.Casino,
            items = gamblingAssetDomains,
            assetPath = "blocklists/gambling_domains.gz",
            bundledDomainCount = 278862
        ),
        PreloadedPack(
            id = "web_social",
            title = "Popular Social Networks",
            description = "12 major social networking and feed platforms",
            icon = Icons.Outlined.Devices,
            items = listOf(
                "facebook.com", "instagram.com", "tiktok.com", "twitter.com",
                "x.com", "reddit.com", "snapchat.com", "pinterest.com",
                "threads.net", "tumblr.com", "linkedin.com", "badoo.com"
            )
        ),
        PreloadedPack(
            id = "web_streaming",
            title = "Video Binge & Streaming",
            description = "10 entertainment, movie, and video streaming sites",
            icon = Icons.Outlined.Movie,
            items = listOf(
                "netflix.com", "hulu.com", "twitch.tv", "disneyplus.com",
                "primevideo.com", "hbomax.com", "vimeo.com", "dailymotion.com",
                "youtube.com", "kick.com"
            )
        ),
        PreloadedPack(
            id = "web_gaming",
            title = "Online Gaming Portals",
            description = "8 browser gaming hubs and online gaming communities",
            icon = Icons.Outlined.SportsEsports,
            items = listOf(
                "roblox.com", "steamcommunity.com", "steampowered.com", "epicgames.com",
                "poki.com", "crazygames.com", "miniclip.com", "armorgames.com"
            )
        )
    )
}
