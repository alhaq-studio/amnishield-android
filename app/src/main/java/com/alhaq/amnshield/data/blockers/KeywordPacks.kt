package com.alhaq.amnshield.data.blockers

/**
 * Curated, precision-filtered Adult & Explicit Keywords.
 * Overly broad words (e.g. "adult", "sex", "explicit", "webcam") have been removed
 * to prevent false positives in educational, medical, and everyday web browsing.
 */
class KeywordPacks {
    companion object {
        val adultKeywords = setOf(
            "porn",
            "pornography",
            "nsfw",
            "xxx",
            "hentai",
            "onlyfans",
            "erotic",
            "nude",
            "nudity",
            "camgirl",
            "striptease",
            "stripper",
            "milf",
            "sexting",
            "topless",
            "masturbate",
            "masturbation",
            "incest",
            "blowjob",
            "escort",
            "prostitute",
            "bdsm",
            "xvideos",
            "pornhub",
            "xnxx",
            "xhamster"
        )
    }
}
