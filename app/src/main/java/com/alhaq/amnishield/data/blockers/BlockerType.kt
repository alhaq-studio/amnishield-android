package com.alhaq.amnishield.data.blockers

/**
 * Identifies the domain/category of a protection rule within the AmniShield Unified Rule System.
 */
enum class BlockerType {
    APP,
    WEBSITE,
    KEYWORD,
    REELS,
    FOCUS_MODE;

    val packageNameIdentifier: String
        get() = when (this) {
            APP -> ""
            WEBSITE -> "website_blocker"
            KEYWORD -> "keyword_blocker"
            REELS -> "reel_blocker"
            FOCUS_MODE -> "FOCUS_MODE"
        }

    companion object {
        fun fromPackageName(packageName: String?): BlockerType {
            if (packageName.isNullOrBlank()) return APP
            return when {
                packageName.equals("website_blocker", ignoreCase = true) -> WEBSITE
                packageName.equals("keyword_blocker", ignoreCase = true) -> KEYWORD
                packageName.equals("reel_blocker", ignoreCase = true) -> REELS
                packageName.equals("FOCUS_MODE", ignoreCase = true) ||
                packageName.equals("focus_mode", ignoreCase = true) -> FOCUS_MODE
                else -> APP
            }
        }

        fun fromTargetTypeString(targetType: String?): BlockerType {
            if (targetType.isNullOrBlank()) return APP
            return when {
                targetType.equals("Website Blocker", ignoreCase = true) ||
                targetType.equals("website_blocker", ignoreCase = true) -> WEBSITE
                targetType.equals("Keyword Blocker", ignoreCase = true) ||
                targetType.equals("keyword_blocker", ignoreCase = true) -> KEYWORD
                targetType.equals("Reels Blocker", ignoreCase = true) ||
                targetType.equals("reel_blocker", ignoreCase = true) -> REELS
                targetType.equals("Focus Mode", ignoreCase = true) ||
                targetType.equals("FOCUS_MODE", ignoreCase = true) ||
                targetType.equals("focus_mode", ignoreCase = true) -> FOCUS_MODE
                else -> APP
            }
        }
    }
}
