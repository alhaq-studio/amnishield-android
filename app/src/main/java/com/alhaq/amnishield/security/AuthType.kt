package com.alhaq.amnishield.security

/**
 * Authentication and protection level enforced on a blocking rule or system toggle.
 */
enum class AuthType {
    /**
     * Standard rule without PIN protection. Can be edited, disabled, or deleted freely.
     */
    NONE,

    /**
     * Protected by the global Master / Anti-Uninstall bypass PIN.
     */
    GLOBAL_PIN,

    /**
     * Protected by a dedicated, custom per-rule PIN.
     */
    RULE_PIN
}
