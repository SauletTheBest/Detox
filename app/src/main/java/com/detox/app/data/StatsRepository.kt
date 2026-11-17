package com.detox.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.detox.app.UsageAccess
import com.detox.app.data.db.BlockEventEntity
import com.detox.app.data.db.DetoxDatabase
import com.detox.app.domain.AppStat
import com.detox.app.domain.DayStat
import com.detox.app.domain.NightStats
import com.detox.app.domain.PausedApp
import com.detox.app.domain.QuietWindow
import com.detox.app.domain.RuleEngine
import com.detox.app.domain.StatsSnapshot
import com.detox.app.domain.TryReason
import com.detox.app.domain.Tries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Stats, part 2: the database side.
 *
 *   bouncer / Reels guard --recordTry()--> block_event table --GROUP BY queries--> load() --> Stats screen
 *
 * Everything stays in the local database. Old rows are deleted after [KEEP_DAYS].
 */
object StatsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: DetoxDatabase

    const val KEEP_DAYS = 90L
    const val CHART_DAYS = 7
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val NIGHT_SHIFT_SEC = (NightStats.NIGHT_SHIFT_HOURS * 3600).toInt()
    private const val TAG = "Detox"

    /** Called from RulesRepository.init (same database). Also tidies up: forget tries older than 90 days. */
    fun init(database: DetoxDatabase) {
        if (::db.isInitialized) return
        db = database
        scope.launch {
            val deleted = db.statsDao().deleteOlderThan(System.currentTimeMillis() - KEEP_DAYS * DAY_MS)
            if (deleted > 0) Log.i(TAG, "Stats: forgot $deleted tries older than $KEEP_DAYS days")
        }
    }

    /**
     * One "try" happened (block screen, mindful pause, Reels closed).
     * A burst within 5 s is merged into one (Tries.deduper), then one INSERT on a background thread.
     */
    fun recordTry(packageName: String, reason: TryReason, windowKind: QuietWindow.Kind? = null) {
        if (!::db.isInitialized) return
        val now = System.currentTimeMillis()
        if (!Tries.deduper.isNewTry(packageName, reason, now)) return
        scope.launch {
            db.statsDao().insert(
                BlockEventEntity(atMs = now, packageName = packageName, reason = reason.name, windowKind = windowKind?.name)
            )
        }
    }

    /** Emits whenever a try is recorded, so an open Stats screen can refresh itself. */
    fun changes(): Flow<Int> = db.statsDao().observeCount()

    /** For the Detox Home line "Last night: 2 tries". */
    suspend fun lastNightTries(now: LocalDateTime = LocalDateTime.now()): Int = withContext(Dispatchers.IO) {
        val lastNight = NightStats.lastNight(now)
        val fromMs = lastNight.atStartOfDay().toEpochMs()     // that night starts in the evening: plenty of margin
        db.statsDao().nightTries(fromMs, NIGHT_SHIFT_SEC)
            .firstOrNull { LocalDate.parse(it.day) == lastNight }?.tries ?: 0
    }

    /** Everything the Stats screen shows. A handful of small queries + one usage-log read. */
    suspend fun load(
        context: Context,
        pausedApps: List<PausedApp>,
        dayStart: LocalTime,
        now: LocalDateTime = LocalDateTime.now(),
    ): StatsSnapshot = withContext(Dispatchers.IO) {
        val dao = db.statsDao()

        // The last 7 Detox-days (a day starts at your "new day" time, e.g. 05:00), oldest first.
        val todayStart = RuleEngine.dayStartOf(now, dayStart)
        val dayStarts = (CHART_DAYS - 1 downTo 0).map { todayStart.minusDays(it.toLong()) }
        val fromMs = dayStarts.first().toEpochMs()

        val triesByDay = dao.triesPerDay(fromMs, dayStart.toSecondOfDay())
            .associate { LocalDate.parse(it.day) to it.tries }
        val usedMs = UsageAccess.usagePerDay(context, dayStarts, pausedApps.map { it.packageName }.toSet())
        val days = dayStarts.mapIndexed { i, start ->
            val day = start.toLocalDate()
            DayStat(day, triesByDay[day] ?: 0, usedMs?.get(i)?.let { (it / 60_000).toInt() })
        }

        // Names: paused apps know theirs; anything else (e.g. a mindful app) asks Android.
        val names = pausedApps.associate { it.packageName to it.name }
        val topApps = dao.triesPerApp(fromMs).map { AppStat(it.name, names[it.name] ?: labelOf(context, it.name), it.tries) }
        // Unknown reason text (a newer version wrote it) -> skip that row instead of crashing.
        val byReason = dao.triesPerReason(fromMs).mapNotNull { row ->
            runCatching { TryReason.valueOf(row.name) }.getOrNull()?.let { it to row.tries }
        }

        // Nights: since stats started (settings.stats_since_ms), so the streak can't be "made up".
        val sinceMs = db.settingsDao().get()?.statsSinceMs?.takeIf { it > 0 } ?: System.currentTimeMillis()
        val firstNight = NightStats.firstNight(LocalDateTime.ofInstant(Instant.ofEpochMilli(sinceMs), ZoneId.systemDefault()))
        val lastNight = NightStats.lastNight(now)
        val nightRows = dao.nightTries(firstNight.atStartOfDay().toEpochMs(), NIGHT_SHIFT_SEC)
        val nightsWithTries = nightRows.filter { it.tries > 0 }.map { LocalDate.parse(it.day) }.toSet()

        StatsSnapshot(
            days = days,
            todayTries = days.last().tries,
            todayPausedMinutes = days.last().pausedMinutes,
            lastNightTries = nightRows.firstOrNull { LocalDate.parse(it.day) == lastNight }?.tries ?: 0,
            calmNights = NightStats.calmStreak(lastNight, nightsWithTries, firstNight),
            topApps = topApps,
            byReason = byReason,
        )
    }

    private fun LocalDateTime.toEpochMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun labelOf(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg   // uninstalled since: show the package name
    }
}
