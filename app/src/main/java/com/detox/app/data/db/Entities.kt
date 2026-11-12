package com.detox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the `quiet_window` table. Room turns this class into:
 *
 *   CREATE TABLE quiet_window (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, ...)
 *
 * Times are stored as "minutes after midnight" (23:30 -> 1410): easy to compare in SQL.
 * Days are a bit mask (Mon=1, Tue=2, Wed=4 ... Sun=64): one number instead of a second table.
 */
@Entity(tableName = "quiet_window")
data class QuietWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,   // 0 = "new row, database picks the id"
    val name: String,
    val kind: String,
    @ColumnInfo(name = "start_min") val startMin: Int,
    @ColumnInfo(name = "end_min") val endMin: Int,
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    val enabled: Boolean,                                // stored as INTEGER 0/1
)

/** One row of `paused_app`. The package name is already unique, so it IS the primary key. */
@Entity(tableName = "paused_app")
data class PausedAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val label: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /** v2: this app's own limit in minutes per day. Int? -> nullable column; NULL = no own limit. */
    @ColumnInfo(name = "daily_limit_min") val dailyLimitMin: Int? = null,
)

/**
 * v2: a SINGLE-ROW table for settings (id is always 1).
 * Why not key/value rows ("daily_limit" -> "45")? Here every setting has a real column
 * with a real type, so the database itself guarantees an Int is an Int.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "daily_limit_on") val dailyLimitOn: Boolean,
    @ColumnInfo(name = "daily_limit_min") val dailyLimitMin: Int,
    @ColumnInfo(name = "day_start_min") val dayStartMin: Int,
    // v3. defaultValue must match the "DEFAULT ..." in MIGRATION_2_3 exactly,
    // because Room compares the migrated table with this class and refuses to open if they differ.
    @ColumnInfo(name = "strict_mode_on", defaultValue = "0") val strictModeOn: Boolean = false,
    @ColumnInfo(name = "countdown_sec", defaultValue = "60") val countdownSec: Int = 60,
    // v4. Same rule: defaultValue = the "DEFAULT 1" in MIGRATION_3_4. 1 = yes, protection should be on.
    @ColumnInfo(name = "protection_wanted", defaultValue = "1") val protectionWanted: Boolean = true,
)

/**
 * v5: a single-row table (id = 1) with the strict home screen's looks.
 * Every defaultValue matches the "DEFAULT ..." in MIGRATION_4_5 exactly (Room compares them).
 * theme is stored as the enum's name in TEXT ("PAPER", "NIGHT"...): readable in DBeaver,
 * and adding a new theme later needs no migration.
 */
@Entity(tableName = "home_prefs")
data class HomePrefsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "theme", defaultValue = "'PAPER'") val theme: String = "PAPER",
    @ColumnInfo(name = "wallpaper_on", defaultValue = "0") val wallpaperOn: Boolean = false,
    @ColumnInfo(name = "quote_on", defaultValue = "1") val quoteOn: Boolean = true,
    @ColumnInfo(name = "dark_during_quiet", defaultValue = "1") val darkDuringQuiet: Boolean = true,
    @ColumnInfo(name = "screen_time_on", defaultValue = "1") val screenTimeOn: Boolean = true,
    @ColumnInfo(name = "swipe_down_notifications", defaultValue = "1") val swipeDownNotifications: Boolean = true,
    @ColumnInfo(name = "double_tap_lock", defaultValue = "1") val doubleTapLock: Boolean = true,
)

/**
 * v5: one row per app you customised on the home screen (favorite / hidden / mindful).
 * Apps you never touched have no row: "no row" = all defaults, so the table stays small.
 * favorite_pos is nullable: NULL = not a favorite, 0..4 = its place on page 1.
 */
@Entity(tableName = "app_pref")
data class AppPrefEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "favorite_pos") val favoritePos: Int? = null,
    @ColumnInfo(name = "hidden", defaultValue = "0") val hidden: Boolean = false,
    @ColumnInfo(name = "mindful", defaultValue = "0") val mindful: Boolean = false,
)
