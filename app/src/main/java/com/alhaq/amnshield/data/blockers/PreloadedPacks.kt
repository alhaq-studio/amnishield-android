package com.alhaq.amnshield.data.blockers

data class PreloadedPack(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val items: List<String>
)

object PreloadedPacks {
    val keywordPacks = listOf(
        PreloadedPack(
            id = "kw_adult",
            title = "Adult & Explicit Content",
            description = "26 High-precision adult keywords (porn, nsfw, hentai, etc.)",
            iconEmoji = "🔞",
            items = KeywordPacks.adultKeywords.toList()
        ),
        PreloadedPack(
            id = "kw_social",
            title = "Social Media & Short Videos",
            description = "Keywords targeting reels, shorts, and social feeds",
            iconEmoji = "📱",
            items = listOf(
                "instagram", "tiktok", "facebook", "twitter", "x.com",
                "reddit", "snapchat", "reels", "shorts", "tumblr", "pinterest", "threads"
            )
        ),
        PreloadedPack(
            id = "kw_gambling",
            title = "Gambling & Betting",
            description = "Keywords for casino, betting, slots, and lotteries",
            iconEmoji = "🎰",
            items = listOf(
                "casino", "betting", "poker", "gambling", "slots",
                "stake", "bet365", "lottery", "roulette", "jackpot", "betfair"
            )
        ),
        PreloadedPack(
            id = "kw_gaming",
            title = "Gaming & Live Streams",
            description = "Keywords for gaming feeds, streams, and Discord",
            iconEmoji = "🎮",
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
            iconEmoji = "🔞",
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
            iconEmoji = "📱",
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
            iconEmoji = "🎰",
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
            iconEmoji = "📺",
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
            iconEmoji = "🎮",
            items = listOf(
                "roblox.com", "steamcommunity.com", "steampowered.com", "epicgames.com",
                "poki.com", "crazygames.com", "miniclip.com", "armorgames.com"
            )
        )
    )
}
