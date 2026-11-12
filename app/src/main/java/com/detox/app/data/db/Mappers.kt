package com.detox.app.data.db

import com.detox.app.domain.AppPref
import com.detox.app.domain.HomePrefs
import com.detox.app.domain.HomeTheme
import com.detox.app.domain.LimitSettings
import com.detox.app.domain.PausedApp
import com.detox.app.domain.QuietWindow
import com.detox.app.domain.StrictSettings
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Translating between "database shape" (Ints, bit masks) and "app shape" (LocalTime, Set<DayOfWeek>).
 * The domain classes stay clean: they don't know a database exists.
 */
object DbCodec {
    fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOf(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)

    /** Mon -> bit 0 (1), Tue -> bit 1 (2), ... Sun -> bit 6 (64). Every day = 127. */
    fun maskOf(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun daysOf(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}

fun QuietWindowEntity.toDomain() = QuietWindow(
    id = id,
    name = name,
    kind = runCatching { QuietWindow.Kind.valueOf(kind) }.getOrDefault(QuietWindow.Kind.NIGHT),
    start = DbCodec.timeOf(startMin),
    end = DbCodec.timeOf(endMin),
    days = DbCodec.daysOf(daysMask),
    enabled = enabled,
)

fun QuietWindow.toEntity() = QuietWindowEntity(
    id = id,
    name = name,
    kind = kind.name,
    startMin = DbCodec.minutesOf(start),
    endMin = DbCodec.minutesOf(end),
    daysMask = DbCodec.maskOf(days),
    enabled = enabled,
)

fun PausedAppEntity.toDomain() = PausedApp(name = label, packageName = packageName, dailyLimitMin = dailyLimitMin)

fun SettingsEntity.toDomain() = LimitSettings(
    dailyLimitOn = dailyLimitOn,
    dailyLimitMin = dailyLimitMin,
    dayStart = DbCodec.timeOf(dayStartMin),
)

fun SettingsEntity.toStrict() = StrictSettings(
    strictModeOn = strictModeOn,
    countdownSec = countdownSec,
    protectionWanted = protectionWanted,
)

// ---- v5: strict home --------------------------------------------------------------

/** Unknown theme name in the DB (e.g. a newer version wrote one we don't know) -> Paper. */
fun HomePrefsEntity.toDomain() = HomePrefs(
    theme = runCatching { HomeTheme.valueOf(theme) }.getOrDefault(HomeTheme.PAPER),
    wallpaperOn = wallpaperOn,
    quoteOn = quoteOn,
    darkDuringQuiet = darkDuringQuiet,
    screenTimeOn = screenTimeOn,
    swipeDownNotifications = swipeDownNotifications,
    doubleTapLock = doubleTapLock,
)

fun HomePrefs.toEntity() = HomePrefsEntity(
    id = 1,
    theme = theme.name,
    wallpaperOn = wallpaperOn,
    quoteOn = quoteOn,
    darkDuringQuiet = darkDuringQuiet,
    screenTimeOn = screenTimeOn,
    swipeDownNotifications = swipeDownNotifications,
    doubleTapLock = doubleTapLock,
)

fun AppPrefEntity.toDomain() = AppPref(
    packageName = packageName,
    favoritePos = favoritePos,
    hidden = hidden,
    mindful = mindful,
)
