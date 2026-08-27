package com.alhaq.amnishield.data.db

/**
 * Migration definitions for the AmniShield rule persistence layer.
 * Enforces explicit SQL defaults for non-nullable authType and nullable cryptographic fields.
 */
object AmniShieldDatabaseMigrations {

    const val CURRENT_VERSION = 2

    /**
     * SQL migration steps from Version 1 (unauthenticated rules) to Version 2 (granular auth locks).
     */
    val MIGRATION_1_2_STATEMENTS = listOf(
        "ALTER TABLE app_block_rules ADD COLUMN authType TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE app_block_rules ADD COLUMN rulePasswordHash TEXT DEFAULT NULL",
        "ALTER TABLE app_block_rules ADD COLUMN rulePasswordSalt TEXT DEFAULT NULL",
        "ALTER TABLE screen_time_limit_rules ADD COLUMN authType TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE screen_time_limit_rules ADD COLUMN rulePasswordHash TEXT DEFAULT NULL",
        "ALTER TABLE screen_time_limit_rules ADD COLUMN rulePasswordSalt TEXT DEFAULT NULL"
    )
}
