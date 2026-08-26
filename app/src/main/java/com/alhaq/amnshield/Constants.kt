package com.alhaq.amnshield

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
        const val KEYWORD_FEEDBACK_HAND_GESTURE = "HAND_GESTURE"
        const val KEYWORD_FEEDBACK_WARNING_SCREEN = "WARNING_SCREEN"
        const val KEYWORD_FEEDBACK_SILENT = "SILENT"

        // available types for focus mode
        const val FOCUS_MODE_BLOCK_ALL_EX_SELECTED = 1
        const val FOCUS_MODE_BLOCK_SELECTED = 2

        const val AMNSHIELD_WEBSITE_URL = "https://amnishield.com/"
        const val AMNSHIELD_DOCS_URL = "https://amnishield.com/docs/"
        const val AMNSHIELD_SUPPORT_URL = "https://alhaq.uk/support.html"
        const val AMNSHIELD_TERMS_URL = "https://alhaq.uk/legal/terms.html"
        const val AMNSHIELD_MOBILE_PRIVACY_URL = "https://alhaq.uk/legal/privacy.html"
        const val ALHAQ_STUDIO_URL = "https://alhaq.uk"
        const val SUPPORT_EMAIL = "support@alhaq.uk"
        const val DATA_DELETION_URL = "https://amnishield.com/legal/delete-account/"
        const val PRIVACY_POLICY_URL = "https://alhaq.uk/legal/privacy.html"
        const val TERMS_OF_SERVICE_URL = "https://alhaq.uk/legal/terms.html"
    }
}
