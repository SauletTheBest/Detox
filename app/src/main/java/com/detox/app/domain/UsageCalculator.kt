package com.detox.app.domain

/**
 * Turns Android's usage log ("Instagram screen opened at 23:10", "...left at 23:25")
 * into "Instagram was on screen for 15 minutes". Pure Kotlin -> unit-tested.
 *
 * Sessions are tracked per SCREEN (activity), because one app can open several screens.
 */
object UsageCalculator {

    enum class Type { RESUMED, PAUSED, STOP_ALL }   // STOP_ALL = screen off / phone shut down

    data class Event(
        val packageName: String,
        val activity: String,
        val type: Type,
        val atMs: Long,
    )

    /** Milliseconds on screen per app between [fromMs] and [toMs]. */
    fun foregroundMs(events: List<Event>, fromMs: Long, toMs: Long): Map<String, Long> {
        val total = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Long>()   // "pkg/activity" -> when it came on screen
        val seen = mutableSetOf<String>()

        fun add(pkg: String, ms: Long) {
            if (ms > 0) total[pkg] = (total[pkg] ?: 0L) + ms
        }

        for (e in events.sortedBy { it.atMs }) {
            val key = e.packageName + "/" + e.activity
            when (e.type) {
                Type.RESUMED -> openSince.putIfAbsent(key, e.atMs)
                Type.PAUSED -> {
                    // Left without us seeing it open? Then it was already open when our period began.
                    val start = openSince.remove(key) ?: if (key !in seen) fromMs else null
                    if (start != null) add(e.packageName, e.atMs - start)
                }
                Type.STOP_ALL -> {
                    openSince.forEach { (k, start) -> add(k.substringBefore('/'), e.atMs - start) }
                    openSince.clear()
                }
            }
            seen += key
        }
        // Still on screen right now: count up to "now".
        openSince.forEach { (k, start) -> add(k.substringBefore('/'), toMs - start) }
        return total
    }
}
