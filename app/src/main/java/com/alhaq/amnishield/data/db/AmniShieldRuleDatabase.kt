package com.alhaq.amnishield.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Relational SQLite persistence engine for AmniShield blocking and screen-time limit rules.
 *
 * Implements [AmniShieldDatabaseMigrations] (V1 -> V2) for device runtime SQLite storage while
 * providing an automatic in-memory relational fallback when running under pure JVM unit tests
 * where Android's native SQLiteDatabase cursor stubs return default values.
 */
class AmniShieldRuleDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext ?: context,
    AmniShieldDatabaseMigrations.DATABASE_NAME,
    null,
    AmniShieldDatabaseMigrations.CURRENT_VERSION
) {

    private val dbLock = Any()

    // In-memory relational mirror for instant StateFlow emission and JVM unit test fallback
    private val inMemoryAppBlockRules = ConcurrentHashMap<String, AppBlockRule>()
    private val inMemoryScreenTimeRules = ConcurrentHashMap<String, ScreenTimeLimitRule>()

    private val _appBlockRulesFlow = MutableStateFlow<List<AppBlockRule>>(emptyList())
    val appBlockRulesFlow: StateFlow<List<AppBlockRule>> = _appBlockRulesFlow.asStateFlow()

    private val _screenTimeLimitRulesFlow = MutableStateFlow<List<ScreenTimeLimitRule>>(emptyList())
    val screenTimeLimitRulesFlow: StateFlow<List<ScreenTimeLimitRule>> = _screenTimeLimitRulesFlow.asStateFlow()

    init {
        refreshFlowsFromStorage()
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(AmniShieldDatabaseMigrations.CREATE_TABLE_APP_BLOCK_RULES_V2)
        db?.execSQL(AmniShieldDatabaseMigrations.CREATE_TABLE_SCREEN_TIME_LIMIT_RULES_V2)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (db == null) return
        if (oldVersion < 2 && newVersion >= 2) {
            AmniShieldDatabaseMigrations.MIGRATION_1_2_STATEMENTS.forEach { sql ->
                runCatching { db.execSQL(sql) }
            }
        }
    }

    fun hasNativeSqliteDatabase(): Boolean {
        return runCatching { writableDatabase != null }.getOrDefault(false)
    }

    fun upsertAppBlockRule(rule: AppBlockRule) = synchronized(dbLock) {
        inMemoryAppBlockRules[rule.id] = rule
        runCatching {
            val db = writableDatabase
            if (db != null) {
                db.insertWithOnConflict(
                    TABLE_APP_BLOCK_RULES,
                    null,
                    rule.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
        emitCurrentFlows()
    }

    fun deleteAppBlockRule(ruleId: String) = synchronized(dbLock) {
        inMemoryAppBlockRules.remove(ruleId)
        runCatching {
            val db = writableDatabase
            db?.delete(TABLE_APP_BLOCK_RULES, "id = ?", arrayOf(ruleId))
        }
        emitCurrentFlows()
    }

    fun syncAppBlockScheduleRules(rules: List<AppBlockScheduleRule>) = synchronized(dbLock) {
        val entities = rules.map { AppBlockRule.fromAppBlockScheduleRule(it) }
        inMemoryAppBlockRules.clear()
        entities.forEach { inMemoryAppBlockRules[it.id] = it }

        runCatching {
            val db = writableDatabase
            if (db != null) {
                db.beginTransaction()
                try {
                    db.delete(TABLE_APP_BLOCK_RULES, null, null)
                    entities.forEach { entity ->
                        db.insertWithOnConflict(
                            TABLE_APP_BLOCK_RULES,
                            null,
                            entity.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        emitCurrentFlows()
    }

    fun loadAppBlockScheduleRules(): List<AppBlockScheduleRule> = synchronized(dbLock) {
        return@synchronized getAllAppBlockRules().map { it.toAppBlockScheduleRule() }
    }

    fun syncScheduleRules(rules: List<ScheduleRule>) = synchronized(dbLock) {
        val entities = rules.map { AppBlockRule.fromScheduleRule(it) }
        inMemoryAppBlockRules.clear()
        entities.forEach { inMemoryAppBlockRules[it.id] = it }

        runCatching {
            val db = writableDatabase
            if (db != null) {
                db.beginTransaction()
                try {
                    db.delete(TABLE_APP_BLOCK_RULES, null, null)
                    entities.forEach { entity ->
                        db.insertWithOnConflict(
                            TABLE_APP_BLOCK_RULES,
                            null,
                            entity.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        emitCurrentFlows()
    }

    fun loadScheduleRules(): List<ScheduleRule> = synchronized(dbLock) {
        return@synchronized getAllAppBlockRules().map { it.toScheduleRule() }
    }

    fun getAllAppBlockRules(): List<AppBlockRule> = synchronized(dbLock) {
        val sqliteList = runCatching {
            val db = readableDatabase ?: return@runCatching null
            val cursor = db.query(
                TABLE_APP_BLOCK_RULES,
                null,
                null,
                null,
                null,
                null,
                "createdAt ASC"
            ) ?: return@runCatching null
            cursor.use { c ->
                val list = mutableListOf<AppBlockRule>()
                while (c.moveToNext()) {
                    list.add(c.toAppBlockRule())
                }
                list
            }
        }.getOrNull()

        if (!sqliteList.isNullOrEmpty()) {
            inMemoryAppBlockRules.clear()
            sqliteList.forEach { inMemoryAppBlockRules[it.id] = it }
            return@synchronized sqliteList
        }
        return@synchronized inMemoryAppBlockRules.values.sortedBy { it.createdAt }
    }

    fun syncLaunchLimitRules(rules: List<AppLaunchLimitRule>) = synchronized(dbLock) {
        val entities = rules.map { ScreenTimeLimitRule.fromLaunchLimitRule(it) }
        inMemoryScreenTimeRules.clear()
        entities.forEach { inMemoryScreenTimeRules[it.id] = it }

        runCatching {
            val db = writableDatabase
            if (db != null) {
                db.beginTransaction()
                try {
                    db.delete(TABLE_SCREEN_TIME_LIMIT_RULES, null, null)
                    entities.forEach { entity ->
                        db.insertWithOnConflict(
                            TABLE_SCREEN_TIME_LIMIT_RULES,
                            null,
                            entity.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        emitCurrentFlows()
    }

    fun loadLaunchLimitRules(): List<AppLaunchLimitRule> = synchronized(dbLock) {
        return@synchronized getAllScreenTimeLimitRules().map { it.toLaunchLimitRule() }
    }

    fun getAllScreenTimeLimitRules(): List<ScreenTimeLimitRule> = synchronized(dbLock) {
        val sqliteList = runCatching {
            val db = readableDatabase ?: return@runCatching null
            val cursor = db.query(
                TABLE_SCREEN_TIME_LIMIT_RULES,
                null,
                null,
                null,
                null,
                null,
                "createdAt ASC"
            ) ?: return@runCatching null
            cursor.use { c ->
                val list = mutableListOf<ScreenTimeLimitRule>()
                while (c.moveToNext()) {
                    list.add(c.toScreenTimeLimitRule())
                }
                list
            }
        }.getOrNull()

        if (!sqliteList.isNullOrEmpty()) {
            inMemoryScreenTimeRules.clear()
            sqliteList.forEach { inMemoryScreenTimeRules[it.id] = it }
            return@synchronized sqliteList
        }
        return@synchronized inMemoryScreenTimeRules.values.sortedBy { it.createdAt }
    }

    private fun refreshFlowsFromStorage() {
        _appBlockRulesFlow.value = getAllAppBlockRules()
        _screenTimeLimitRulesFlow.value = getAllScreenTimeLimitRules()
    }

    private fun emitCurrentFlows() {
        _appBlockRulesFlow.value = inMemoryAppBlockRules.values.sortedBy { it.createdAt }
        _screenTimeLimitRulesFlow.value = inMemoryScreenTimeRules.values.sortedBy { it.createdAt }
    }

    private fun AppBlockRule.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("packageName", packageName)
        put("type", type)
        put("recurrence", recurrence)
        put("startMinute", startMinute)
        put("endMinute", endMinute)
        put("selectedDays", selectedDays)
        put("durationHours", durationHours)
        put("activeUntilMillis", activeUntilMillis)
        put("createdAt", createdAt)
        put("groupId", groupId)
        put("groupTitle", groupTitle)
        put("isEnabled", if (isEnabled) 1 else 0)
        put("authType", authType.name)
        put("rulePasswordHash", rulePasswordHash)
        put("rulePasswordSalt", rulePasswordSalt)
    }

    private fun ScreenTimeLimitRule.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("packageName", packageName)
        put("maxLaunches", maxLaunches)
        put("timePeriod", timePeriod)
        put("dayOfWeek", dayOfWeek)
        put("limitMinutes", limitMinutes)
        put("createdAt", createdAt)
        put("isEnabled", if (isEnabled) 1 else 0)
        put("authType", authType.name)
        put("rulePasswordHash", rulePasswordHash)
        put("rulePasswordSalt", rulePasswordSalt)
    }

    private fun Cursor.toAppBlockRule(): AppBlockRule {
        val authStr = getStringOrDefault("authType", AuthType.NONE.name)
        val parsedAuth = runCatching { AuthType.valueOf(authStr) }.getOrDefault(AuthType.NONE)
        return AppBlockRule(
            id = getStringOrDefault("id", ""),
            title = getStringOrDefault("title", ""),
            packageName = getStringOrDefault("packageName", ""),
            type = getStringOrDefault("type", "BLOCK"),
            recurrence = getStringOrDefault("recurrence", "ALWAYS"),
            startMinute = getIntOrDefault("startMinute", 0),
            endMinute = getIntOrDefault("endMinute", 0),
            selectedDays = getStringOrDefault("selectedDays", ""),
            durationHours = getIntOrDefault("durationHours", 0),
            activeUntilMillis = getLongOrDefault("activeUntilMillis", 0L),
            createdAt = getLongOrDefault("createdAt", 0L),
            groupId = getNullableString("groupId"),
            groupTitle = getNullableString("groupTitle"),
            isEnabled = getIntOrDefault("isEnabled", 1) != 0,
            authType = parsedAuth,
            rulePasswordHash = getNullableString("rulePasswordHash"),
            rulePasswordSalt = getNullableString("rulePasswordSalt")
        )
    }

    private fun Cursor.toScreenTimeLimitRule(): ScreenTimeLimitRule {
        val authStr = getStringOrDefault("authType", AuthType.NONE.name)
        val parsedAuth = runCatching { AuthType.valueOf(authStr) }.getOrDefault(AuthType.NONE)
        return ScreenTimeLimitRule(
            id = getStringOrDefault("id", ""),
            packageName = getStringOrDefault("packageName", ""),
            maxLaunches = getIntOrDefault("maxLaunches", 0),
            timePeriod = getStringOrDefault("timePeriod", "DAILY"),
            dayOfWeek = getIntOrDefault("dayOfWeek", 0),
            limitMinutes = getIntOrDefault("limitMinutes", 0),
            createdAt = getLongOrDefault("createdAt", 0L),
            isEnabled = getIntOrDefault("isEnabled", 1) != 0,
            authType = parsedAuth,
            rulePasswordHash = getNullableString("rulePasswordHash"),
            rulePasswordSalt = getNullableString("rulePasswordSalt")
        )
    }

    private fun Cursor.getStringOrDefault(column: String, default: String): String {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) ?: default else default
    }

    private fun Cursor.getNullableString(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getIntOrDefault(column: String, default: Int): Int {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }

    private fun Cursor.getLongOrDefault(column: String, default: Long): Long {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
    }

    companion object {
        const val TABLE_APP_BLOCK_RULES = "app_block_rules"
        const val TABLE_SCREEN_TIME_LIMIT_RULES = "screen_time_limit_rules"

        private val instances = ConcurrentHashMap<Context, AmniShieldRuleDatabase>()

        fun getInstance(context: Context): AmniShieldRuleDatabase {
            val key = context.applicationContext ?: context
            return instances.getOrPut(key) { AmniShieldRuleDatabase(key) }
        }

        fun clearInstances() {
            instances.clear()
        }
    }
}
