package com.alhaq.amnishield.data.db

/**
 * Migration definitions for the AmniShield rule persistence layer.
 * Enforces explicit SQL defaults for non-nullable authType and nullable cryptographic fields.
 */
object AmniShieldDatabaseMigrations {

    const val DATABASE_NAME = "amnishield_rules.db"
    const val CURRENT_VERSION = 2

    const val CREATE_TABLE_APP_BLOCK_RULES_V2 = """
        CREATE TABLE IF NOT EXISTS app_block_rules (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            packageName TEXT NOT NULL,
            type TEXT NOT NULL,
            recurrence TEXT NOT NULL,
            startMinute INTEGER NOT NULL,
            endMinute INTEGER NOT NULL,
            selectedDays TEXT NOT NULL,
            durationHours INTEGER NOT NULL,
            activeUntilMillis INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            groupId TEXT,
            groupTitle TEXT,
            isEnabled INTEGER NOT NULL,
            authType TEXT NOT NULL DEFAULT 'NONE',
            rulePasswordHash TEXT DEFAULT NULL,
            rulePasswordSalt TEXT DEFAULT NULL
        )
    """

    const val CREATE_TABLE_SCREEN_TIME_LIMIT_RULES_V2 = """
        CREATE TABLE IF NOT EXISTS screen_time_limit_rules (
            id TEXT PRIMARY KEY,
            packageName TEXT NOT NULL,
            maxLaunches INTEGER NOT NULL,
            timePeriod TEXT NOT NULL,
            dayOfWeek INTEGER NOT NULL,
            limitMinutes INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            isEnabled INTEGER NOT NULL,
            authType TEXT NOT NULL DEFAULT 'NONE',
            rulePasswordHash TEXT DEFAULT NULL,
            rulePasswordSalt TEXT DEFAULT NULL
        )
    """

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
