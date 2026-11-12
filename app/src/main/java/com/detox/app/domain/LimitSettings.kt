package com.detox.app.domain

import java.time.LocalTime

/**
 * Daily budget for ALL paused apps together, plus when a "day" starts.
 *
 * dayStart = 05:00 instead of midnight: otherwise a fresh limit would start at 00:00,
 * exactly when late-night scrolling happens.
 */
data class LimitSettings(
    val dailyLimitOn: Boolean = false,
    val dailyLimitMin: Int = 45,
    val dayStart: LocalTime = LocalTime.of(5, 0),
)
