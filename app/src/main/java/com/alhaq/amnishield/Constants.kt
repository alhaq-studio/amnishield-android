package com.alhaq.amnishield

class Constants {
    companion object {
        // available modes for setting up anti-uninstall
        const val ANTI_UNINSTALL_PASSWORD_MODE = 1
        const val ANTI_UNINSTALL_TIMED_MODE = 2

        // available types of warning screen
        const val WARNING_SCREEN_MODE_VIEW_BLOCKER = 1
        const val WARNING_SCREEN_MODE_APP_BLOCKER = 2
        const val WARNING_SCREEN_MODE_KEYWORD_BLOCKER = 3

        // available feedback modes for keyword blocker
        const val KEYWORD_FEEDBACK_AMNISPACE = "AMNISPACE"
        const val KEYWORD_FEEDBACK_WARNING_SCREEN = "WARNING_SCREEN"
        const val KEYWORD_FEEDBACK_SILENT = "SILENT"

        // available blocker warning screen styles (AmniSpace, Standard Dialog, Silent)
        const val BLOCKER_WARNING_STYLE_AMNISPACE = "AMNISPACE"
        const val BLOCKER_WARNING_STYLE_DIALOG = "DIALOG"
        const val BLOCKER_WARNING_STYLE_SILENT = "SILENT"

        // available types for focus mode
        const val FOCUS_MODE_BLOCK_ALL_EX_SELECTED = 1
        const val FOCUS_MODE_BLOCK_SELECTED = 2

        // AmniSpace (Mindful Focus Launcher Space) modes & extras
        const val AMNISPACE_MODE_FOCUS_LAUNCHER = 1
        const val AMNISPACE_MODE_MINDFUL_BREATHING = 2
        const val AMNISPACE_EXTRA_MODE = "extra_amnispace_mode"
        const val AMNISPACE_EXTRA_TRIGGER_REASON = "extra_trigger_reason"
        const val AMNISPACE_EXTRA_TRIGGER_APP = "extra_trigger_app"
        const val AMNISPACE_EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val AMNISPACE_DEFAULT_BREATHING_SECS = 5

        const val AMNISHIELD_WEBSITE_URL = "https://amnishield.com/"
        const val AMNISHIELD_DOCS_URL = "https://amnishield.com/docs/"
        const val AMNISHIELD_SUPPORT_URL = "https://alhaq.uk/support.html"
        const val AMNISHIELD_TERMS_URL = "https://alhaq.uk/legal/terms.html"
        const val AMNISHIELD_MOBILE_PRIVACY_URL = "https://alhaq.uk/legal/privacy.html"
        const val ALHAQ_STUDIO_URL = "https://alhaq.uk"
        const val SUPPORT_EMAIL = "support@alhaq.uk"
        const val DATA_DELETION_URL = "https://amnishield.com/legal/delete-account/"
        const val PRIVACY_POLICY_URL = "https://alhaq.uk/legal/privacy.html"
        const val TERMS_OF_SERVICE_URL = "https://alhaq.uk/legal/terms.html"

        // GitHub & Source Code
        const val GITHUB_REPO_URL = "https://github.com/alhaq-studio/amnishield-android"
        const val GITHUB_SPONSORS_PERSONAL_URL = "https://github.com/sponsors/Afrasyaab-GH"
        const val GITHUB_SPONSORS_INITIATIVE_URL = "https://github.com/sponsors/alhaq-initiative"

        // Donation & Tip Platforms (from FUNDING.yml)
        const val KOFI_URL = "https://ko-fi.com/alhaq"
        const val PATREON_URL = "https://www.patreon.com/alhaq"
        const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/alhaq"
        const val ALHAQ_INITIATIVE_URL = "https://alhaq-initiative.org"
        const val ALHAQ_INITIATIVE_DONATE_URL = "https://alhaq-initiative.org/donate"

        // Community & Social
        const val TELEGRAM_URL = "https://t.me/amnishield"
        const val DISCORD_URL = "https://discord.gg/zXz7pGVJY"
    }
}
