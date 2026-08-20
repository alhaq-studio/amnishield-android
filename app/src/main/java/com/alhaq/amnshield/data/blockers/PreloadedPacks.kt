package com.alhaq.amnshield.data.blockers

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
    val items: List<String>
)

object PreloadedPacks {
    val keywordPacks = listOf(
        PreloadedPack(
            id = "kw_adult",
            title = "Adult & Explicit Content",
            description = "26 High-precision adult keywords (porn, nsfw, hentai, etc.)",
            icon = Icons.Outlined.Shield,
            items = KeywordPacks.adultKeywords.toList()
        ),
        PreloadedPack(
            id = "kw_social",
            title = "Social Media & Short Videos",
            description = "Keywords targeting reels, shorts, and social feeds",
            icon = Icons.Outlined.Devices,
            items = listOf(
                "instagram", "tiktok", "facebook", "twitter", "x.com",
                "reddit", "snapchat", "reels", "shorts", "tumblr", "pinterest", "threads"
            )
        ),
        PreloadedPack(
            id = "kw_gambling",
            title = "Gambling & Betting",
            description = "Keywords for casino, betting, slots, and lotteries",
            icon = Icons.Outlined.Casino,
            items = listOf(
                "casino", "betting", "poker", "gambling", "slots",
                "stake", "bet365", "lottery", "roulette", "jackpot", "betfair"
            )
        ),
        PreloadedPack(
            id = "kw_gaming",
            title = "Gaming & Live Streams",
            description = "Keywords for gaming feeds, streams, and Discord",
            icon = Icons.Outlined.SportsEsports,
            items = listOf(
                "twitch", "steam", "roblox", "minecraft", "kick.com", "discord", "fortnite"
            )
        )
    )

    val websitePacks = listOf(
        PreloadedPack(
            id = "web_adult",
            title = "Adult & Explicit Websites",
            description = "Top adult & explicit streaming domains",
            icon = Icons.Outlined.Shield,
            items = listOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "onlyfans.com",
                "xhamster.com", "stripchat.com", "redtube.com", "chaturbate.com",
                "youporn.com", "spankbang.com", "tube8.com", "beeg.com",
                "brazzers.com", "eporner.com", "livejasmin.com", "xvideos2.com",
                "youjizz.com", "bongacams.com", "hentaihaven.xxx", "hqporner.com"
            )
        ),
        PreloadedPack(
            id = "web_social",
            title = "Popular Social Networks",
            description = "Major social networking and feed platforms",
            icon = Icons.Outlined.Devices,
            items = listOf(
                "facebook.com", "instagram.com", "tiktok.com", "twitter.com",
                "x.com", "reddit.com", "snapchat.com", "pinterest.com",
                "threads.net", "tumblr.com", "linkedin.com", "badoo.com"
            )
        ),
        PreloadedPack(
            id = "web_gambling",
            title = "Gambling & Betting Sites",
            description = "Online casinos, sportsbooks, and crypto gambling",
            icon = Icons.Outlined.Casino,
            items = listOf(
                "stake.com", "bet365.com", "bwin.com", "pokerstars.com",
                "roobet.com", "draftkings.com", "fanduel.com", "888casino.com",
                "betfair.com", "1xbet.com", "22bet.com", "unibet.com",
                "betway.com", "williamhill.com", "paddypower.com"
            )
        ),
        PreloadedPack(
            id = "web_streaming",
            title = "Video Binge & Streaming",
            description = "Entertainment, movie, and video streaming sites",
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
            description = "Browser gaming hubs and online gaming communities",
            icon = Icons.Outlined.SportsEsports,
            items = listOf(
                "roblox.com", "steamcommunity.com", "steampowered.com", "epicgames.com",
                "poki.com", "crazygames.com", "miniclip.com", "armorgames.com"
            )
        )
    )
}
