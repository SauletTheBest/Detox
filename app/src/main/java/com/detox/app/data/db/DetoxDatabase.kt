package com.detox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The database file: /data/data/com.detox.app/databases/detox.db (private to this app).
 *
 * Version history:
 *   1 - quiet_window, paused_app
 *   2 - paused_app.daily_limit_min + settings table (daily limits)
 *   3 - settings.strict_mode_on + settings.countdown_sec (strict mode, countdown)
 *   4 - settings.protection_wanted (self-healing protection)
 *   5 - home_prefs (strict home looks) + app_pref (favorites / hidden / mindful apps)
 *   6 - settings.reels_guard_wanted (optional Reels & Shorts guard, self-healing)
 *   7 - block_event (stats: one row per try) + settings.stats_since_ms
 *   8 - own_quote + builtin_quote_pref (your quotes) + home_prefs.only_liked_quotes
 */
@Database(
    entities = [
        QuietWindowEntity::class, PausedAppEntity::class, SettingsEntity::class,
        HomePrefsEntity::class, AppPrefEntity::class, BlockEventEntity::class,
        OwnQuoteEntity::class, BuiltinQuotePrefEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class DetoxDatabase : RoomDatabase() {
    abstract fun quietWindowDao(): QuietWindowDao
    abstract fun pausedAppDao(): PausedAppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun homePrefsDao(): HomePrefsDao
    abstract fun appPrefDao(): AppPrefDao
    abstract fun statsDao(): StatsDao
    abstract fun quotesDao(): QuotesDao

    companion object {
        fun create(context: Context): DetoxDatabase =
            Room.databaseBuilder(context, DetoxDatabase::class.java, "detox.db")
                // Room chains them: 1 -> 2 -> ... -> 8 (a phone on v7 runs only 7_8)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .addCallback(SeedDefaults)
                .build()
    }
}

/**
 * Upgrades a version-1 database (already on your phone, with your rules in it) to version 2
 * WITHOUT losing data. Room runs this once, inside a transaction: all or nothing.
 * Afterwards Room checks the result matches the entities exactly, or it refuses to open.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing rows get NULL in the new column = "no own limit". Nothing is lost.
        db.execSQL("ALTER TABLE paused_app ADD COLUMN daily_limit_min INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS settings (
              id              INTEGER NOT NULL,
              daily_limit_on  INTEGER NOT NULL,
              daily_limit_min INTEGER NOT NULL,
              day_start_min   INTEGER NOT NULL,
              PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(INSERT_DEFAULT_SETTINGS)
    }
}

/**
 * v2 -> v3: two new columns. SQLite needs a DEFAULT when adding a NOT NULL column,
 * so the existing row gets: strict mode off, 60-second countdown.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN strict_mode_on INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE settings ADD COLUMN countdown_sec INTEGER NOT NULL DEFAULT 60")
    }
}

/**
 * v3 -> v4: one new column. The existing row gets 1 = "yes, I want protection on",
 * which is true for everyone who already uses Detox.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN protection_wanted INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v4 -> v5: two NEW tables, nothing changes in the old ones.
 * The column lines must match HomePrefsEntity / AppPrefEntity exactly (type, NOT NULL, DEFAULT),
 * because after migrating Room compares the real table with the class and refuses a mismatch.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS home_prefs (
              id                       INTEGER NOT NULL,
              theme                    TEXT    NOT NULL DEFAULT 'PAPER',
              wallpaper_on             INTEGER NOT NULL DEFAULT 0,
              quote_on                 INTEGER NOT NULL DEFAULT 1,
              dark_during_quiet        INTEGER NOT NULL DEFAULT 1,
              screen_time_on           INTEGER NOT NULL DEFAULT 1,
              swipe_down_notifications INTEGER NOT NULL DEFAULT 1,
              double_tap_lock          INTEGER NOT NULL DEFAULT 1,
              PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(INSERT_DEFAULT_HOME_PREFS)

        // favorite_pos has no NOT NULL: NULL = "not a favorite".
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_pref (
              package_name TEXT    NOT NULL,
              favorite_pos INTEGER,
              hidden       INTEGER NOT NULL DEFAULT 0,
              mindful      INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(package_name)
            )
            """.trimIndent()
        )
    }
}

/**
 * v5 -> v6: one new column, same pattern as MIGRATION_3_4. The existing row gets 0 =
 * "Reels guard not wanted": it's optional, so nobody gets it switched on by an update.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN reels_guard_wanted INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v6 -> v7: stats. The first migration that also FILLS IN data, not only structure:
 *   1) a new table + an index on the time column
 *   2) a new settings column, then an UPDATE that writes "now" into it
 *      ("?" + an argument = a bound parameter, like $1 in Go's database/sql: no string gluing).
 * The CREATE lines must match BlockEventEntity exactly (Room compares tables AND indexes).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS block_event (
              id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              at_ms        INTEGER NOT NULL,
              package_name TEXT    NOT NULL,
              reason       TEXT    NOT NULL,
              window_kind  TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_block_event_at_ms ON block_event (at_ms)")
        db.execSQL("ALTER TABLE settings ADD COLUMN stats_since_ms INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE settings SET stats_since_ms = ? WHERE id = 1", arrayOf<Any?>(System.currentTimeMillis()))
    }
}

/**
 * v7 -> v8: quotes. Two new tables (nothing to fill in: they start empty) and one new column.
 * The CREATE lines must match OwnQuoteEntity / BuiltinQuotePrefEntity exactly.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS own_quote (
              id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              quote_text TEXT    NOT NULL,
              author     TEXT,
              liked      INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS builtin_quote_pref (
              quote_text TEXT    NOT NULL,
              liked      INTEGER NOT NULL DEFAULT 0,
              hidden     INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(quote_text)
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE home_prefs ADD COLUMN only_liked_quotes INTEGER NOT NULL DEFAULT 0")
    }
}

/** The single home_prefs row; every column not listed takes its DEFAULT (Paper, quote on ...). */
private const val INSERT_DEFAULT_HOME_PREFS = "INSERT OR IGNORE INTO home_prefs (id) VALUES (1)"

/**
 * Shared by the migration and fresh installs: limit off, 45 min, day starts 05:00 (300 min).
 * strict_mode_on / countdown_sec / protection_wanted / reels_guard_wanted are not listed
 * -> their DEFAULT values are used.
 */
private const val INSERT_DEFAULT_SETTINGS =
    "INSERT INTO settings (id, daily_limit_on, daily_limit_min, day_start_min) VALUES (1, 0, 45, 300)"

/** Runs ONCE, when the database file is first created (fresh install): the starting rules. Plain SQL. */
private object SeedDefaults : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO quiet_window (name, kind, start_min, end_min, days_mask, enabled) VALUES
              ('Night',   'NIGHT',   1410, 420, 127, 1),  -- 23:30 -> 07:00, every day
              ('Morning', 'MORNING',  420, 540, 127, 1)   -- 07:00 -> 09:00, every day
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO paused_app (package_name, label, added_at) VALUES
              ('com.instagram.android',      'Instagram', $now),
              ('com.instagram.barcelona',    'Threads',   $now),
              ('com.google.android.youtube', 'YouTube',   $now),
              ('com.pinterest',              'Pinterest', $now)
            """.trimIndent()
        )
        db.execSQL(INSERT_DEFAULT_SETTINGS)
        // v7: stats start now (a separate UPDATE, because INSERT_DEFAULT_SETTINGS is shared with
        // MIGRATION_1_2, which runs on old databases that don't have this column yet).
        db.execSQL("UPDATE settings SET stats_since_ms = ? WHERE id = 1", arrayOf<Any?>(now))
        db.execSQL(INSERT_DEFAULT_HOME_PREFS)   // v5; app_pref starts empty; block_event starts empty
    }
}
