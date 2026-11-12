package com.detox.app.domain

/**
 * The "mindful pause" rule, pure Kotlin (you pass the time in, so tests need no clock).
 *
 *   You open YouTube (marked "mindful")  -> needsPause = true  -> "Take a breath" screen
 *   You tap "Open YouTube" after 5 s     -> allow()            -> no pause for 10 minutes
 *   10 minutes later you open it again   -> needsPause = true  -> pause again
 *
 * Without the 10-minute grace, every screen YouTube opens (splash -> main) would trigger
 * the pause again and you could never get in.
 *
 * Lives only in memory: if Android restarts Detox, the grace is forgotten and you simply
 * get one more pause. That's fine for a gentle reminder.
 */
class MindfulGate(private val graceMs: Long = GRACE_MS) {

    // package name -> until when (epoch ms) the app may open without a pause.
    // Like a Go map[string]int64. @Synchronized below = a mutex around every access.
    private val allowedUntil = HashMap<String, Long>()

    @Synchronized
    fun needsPause(packageName: String, nowMs: Long): Boolean {
        val until = allowedUntil[packageName] ?: return true
        return nowMs >= until
    }

    /** You waited and chose to open it: let it through for the next [graceMs]. */
    @Synchronized
    fun allow(packageName: String, nowMs: Long) {
        allowedUntil[packageName] = nowMs + graceMs
    }

    companion object {
        const val GRACE_MS = 10 * 60 * 1000L     // 10 minutes
        const val PAUSE_SECONDS = 5              // how long the "Take a breath" screen waits
    }
}

/** The ONE gate shared by the bouncer service (asks) and the pause screen (allows). */
object Mindful {
    val gate = MindfulGate()
}
