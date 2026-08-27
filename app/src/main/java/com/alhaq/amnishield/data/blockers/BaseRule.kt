package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.security.AuthType

/**
 * Common security interface implemented by all rule entities in AmniShield.
 */
interface BaseRule {
    val id: String
    val authType: AuthType
    val rulePasswordHash: String?
    val rulePasswordSalt: String?
}
