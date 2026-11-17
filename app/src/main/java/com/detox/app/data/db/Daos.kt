package com.detox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.detox.app.domain.Favorites
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object: the list of SQL queries we're allowed to run.
 * Room checks every query against the tables WHILE COMPILING: a typo in a column
 * name is a build error, not a crash on your phone.
 *
 * Flow<...> = "live query": Room re-runs it and sends a new list every time the table changes.
 * suspend  = runs on a background thread (like a goroutine), never freezes the screen.
 */
@Dao
interface QuietWindowDao {

    @Query("SELECT * FROM quiet_window ORDER BY id")
    fun observeAll(): Flow<List<QuietWindowEntity>>

    /** INSERT if the id is new (0), UPDATE if it already exists. */
    @Upsert
    suspend fun upsert(window: QuietWindowEntity)

    @Query("UPDATE quiet_window SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("DELETE FROM quiet_window WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface PausedAppDao {

    // COLLATE NOCASE: sort "instagram" and "Instagram" together.
    @Query("SELECT * FROM paused_app ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<PausedAppEntity>>

    /** IGNORE: adding an app that is already paused does nothing (no error). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: PausedAppEntity)

    @Query("DELETE FROM paused_app WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    /** minutes = null writes SQL NULL ("no own limit"). */
    @Query("UPDATE paused_app SET daily_limit_min = :minutes WHERE package_name = :packageName")
    suspend fun setLimit(packageName: String, minutes: Int?)
}

@Dao
interface SettingsDao {

    // Flow<SettingsEntity?>: null if the row doesn't exist (shouldn't happen, but we stay safe).
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    /** One-time read (not live). Used by the home screen right when Home is pressed. */
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    // Targeted UPDATEs instead of replacing the whole row: saving the daily limit must not
    // overwrite strict mode with an old value (the classic "lost update" problem).
    @Query("UPDATE settings SET daily_limit_on = :on, daily_limit_min = :minutes, day_start_min = :dayStartMin WHERE id = 1")
    suspend fun updateLimits(on: Boolean, minutes: Int, dayStartMin: Int)

    @Query("UPDATE settings SET strict_mode_on = :on WHERE id = 1")
    suspend fun setStrictMode(on: Boolean)

    @Query("UPDATE settings SET countdown_sec = :seconds WHERE id = 1")
    suspend fun setCountdown(seconds: Int)

    @Query("UPDATE settings SET protection_wanted = :wanted WHERE id = 1")
    suspend fun setProtectionWanted(wanted: Boolean)

    // v6: Room turns Boolean into 0/1 for SQLite automatically.
    @Query("UPDATE settings SET reels_guard_wanted = :wanted WHERE id = 1")
    suspend fun setReelsGuardWanted(wanted: Boolean)
}

/** v5: the strict home screen's looks (single row, id = 1). */
@Dao
interface HomePrefsDao {

    @Query("SELECT * FROM home_prefs WHERE id = 1")
    fun observe(): Flow<HomePrefsEntity?>

    /**
     * Whole-row save. Here that's safe: only the Settings screen edits these looks, so there's
     * no second writer whose change we could overwrite. @Upsert also re-creates the row
     * if it were ever missing.
     */
    @Upsert
    suspend fun save(prefs: HomePrefsEntity)
}

/**
 * v5: per-app choices (favorite / hidden / mindful).
 *
 * An abstract class (not an interface) because some functions below have a BODY:
 * several SQL steps wrapped in @Transaction = all or nothing, like BEGIN ... COMMIT.
 */
@Dao
abstract class AppPrefDao {

    @Query("SELECT * FROM app_pref")
    abstract fun observeAll(): Flow<List<AppPrefEntity>>

    // ---- small SQL building blocks (use the "commands" below instead of calling these) ----

    /** Make sure the app has a row (all defaults). Does nothing if it already has one. */
    @Query("INSERT OR IGNORE INTO app_pref (package_name) VALUES (:packageName)")
    abstract suspend fun ensureRow(packageName: String)

    @Query("UPDATE app_pref SET hidden = :hidden WHERE package_name = :packageName")
    abstract suspend fun updateHidden(packageName: String, hidden: Boolean)

    @Query("UPDATE app_pref SET mindful = :mindful WHERE package_name = :packageName")
    abstract suspend fun updateMindful(packageName: String, mindful: Boolean)

    @Query("SELECT COUNT(*) FROM app_pref WHERE favorite_pos IS NOT NULL")
    abstract suspend fun favoriteCount(): Int

    @Query("SELECT favorite_pos FROM app_pref WHERE package_name = :packageName")
    abstract suspend fun favoritePosOf(packageName: String): Int?

    // MAX() skips NULLs. No favorites yet -> MAX is NULL -> COALESCE turns it into -1 -> -1 + 1 = 0.
    @Query(
        "UPDATE app_pref SET favorite_pos = " +
            "(SELECT COALESCE(MAX(favorite_pos), -1) + 1 FROM app_pref) " +
            "WHERE package_name = :packageName"
    )
    abstract suspend fun appendFavorite(packageName: String)

    @Query("UPDATE app_pref SET favorite_pos = NULL WHERE package_name = :packageName")
    abstract suspend fun clearFavorite(packageName: String)

    @Query("SELECT package_name FROM app_pref WHERE favorite_pos IS NOT NULL ORDER BY favorite_pos")
    abstract suspend fun favoritesInOrder(): List<String>

    @Query("UPDATE app_pref SET favorite_pos = :position WHERE package_name = :packageName")
    abstract suspend fun setFavoritePos(packageName: String, position: Int)

    // ---- the public "commands" (each one is a transaction) ---------------------------

    @Transaction
    open suspend fun setHidden(packageName: String, hidden: Boolean) {
        ensureRow(packageName)
        updateHidden(packageName, hidden)
    }

    @Transaction
    open suspend fun setMindful(packageName: String, mindful: Boolean) {
        ensureRow(packageName)
        updateMindful(packageName, mindful)
    }

    /** @return false = page 1 is full (already [max] favorites). Already a favorite = true. */
    @Transaction
    open suspend fun addFavorite(packageName: String, max: Int): Boolean {
        ensureRow(packageName)
        if (favoritePosOf(packageName) != null) return true
        if (favoriteCount() >= max) return false
        appendFavorite(packageName)   // goes to the end of the list
        return true
    }

    /** Remove from favorites, then close the gap so positions stay 0, 1, 2 ... */
    @Transaction
    open suspend fun removeFavorite(packageName: String) {
        clearFavorite(packageName)
        Favorites.renumber(favoritesInOrder()).forEach { (pkg, position) -> setFavoritePos(pkg, position) }
    }
}

/**
 * v7: stats. Writes are one INSERT per try; reads are small GROUP BY queries.
 * All reads are "since fromMs", so the index on at_ms makes them fast even after months.
 */
@Dao
interface StatsDao {

    @Insert
    suspend fun insert(event: BlockEventEntity)

    /** Keep the table small: forget tries older than the cutoff. Returns how many rows were deleted. */
    @Query("DELETE FROM block_event WHERE at_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    /**
     * Tries per Detox-day. A Detox-day starts at your "new day" time (e.g. 05:00), so a try
     * at 02:00 still belongs to yesterday. Trick: subtract dayStartSec, THEN take the date.
     *   at_ms / 1000            -> seconds since 1970 (SQLite's 'unixepoch' wants seconds)
     *   'localtime'             -> your phone's time zone, not UTC
     */
    @Query(
        """
        SELECT date(at_ms / 1000 - :dayStartSec, 'unixepoch', 'localtime') AS day, COUNT(*) AS tries
        FROM block_event
        WHERE at_ms >= :fromMs
        GROUP BY day
        ORDER BY day
        """
    )
    suspend fun triesPerDay(fromMs: Long, dayStartSec: Int): List<DayCountRow>

    /** Which apps you tried most. `AS name` = the column name Room puts into NameCountRow.name. */
    @Query(
        """
        SELECT package_name AS name, COUNT(*) AS tries
        FROM block_event
        WHERE at_ms >= :fromMs
        GROUP BY package_name
        ORDER BY tries DESC
        """
    )
    suspend fun triesPerApp(fromMs: Long): List<NameCountRow>

    /** Why Detox stepped in (quiet time, limit, Reels...). */
    @Query(
        """
        SELECT reason AS name, COUNT(*) AS tries
        FROM block_event
        WHERE at_ms >= :fromMs
        GROUP BY reason
        ORDER BY tries DESC
        """
    )
    suspend fun triesPerReason(fromMs: Long): List<NameCountRow>

    /**
     * Night-time tries per night. Same shift trick with 12 hours (NightStats.NIGHT_SHIFT_HOURS):
     * Mon 23:45 and Tue 06:30 both become "Monday" -> one night = one date.
     */
    @Query(
        """
        SELECT date(at_ms / 1000 - :nightShiftSec, 'unixepoch', 'localtime') AS day, COUNT(*) AS tries
        FROM block_event
        WHERE reason = 'QUIET' AND window_kind = 'NIGHT' AND at_ms >= :fromMs
        GROUP BY day
        """
    )
    suspend fun nightTries(fromMs: Long, nightShiftSec: Int): List<DayCountRow>

    /** Live row count: the Stats screen reloads when a new try is recorded. */
    @Query("SELECT COUNT(*) FROM block_event")
    fun observeCount(): Flow<Int>
}

/** One result row of a "per day" query: day = 'YYYY-MM-DD' (SQLite's date() format). */
data class DayCountRow(val day: String, val tries: Int)

/** One result row of a "per app" / "per reason" query. */
data class NameCountRow(val name: String, val tries: Int)
