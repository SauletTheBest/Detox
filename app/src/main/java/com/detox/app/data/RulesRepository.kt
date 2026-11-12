package com.detox.app.data

import android.content.Context
import com.detox.app.data.db.DetoxDatabase
import com.detox.app.data.db.PausedAppEntity
import com.detox.app.data.db.toDomain
import com.detox.app.data.db.toEntity
import com.detox.app.data.db.DbCodec
import com.detox.app.data.db.toStrict
import com.detox.app.domain.AppPref
import com.detox.app.domain.Favorites
import com.detox.app.domain.HomePrefs
import com.detox.app.domain.LimitSettings
import com.detox.app.domain.PausedApp
import com.detox.app.domain.QuietWindow
import com.detox.app.domain.StrictSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ONE place that holds the rules. The Home screen and the bouncer service both read
 * [windows] and [apps]; both stay in sync with the database automatically:
 *
 *   database table --(Room live query)--> StateFlow --> screen redraws / service decides
 *
 * StateFlow ~ in Go: a value behind a mutex + a channel that tells every listener "it changed".
 * Writes go to the database on a background thread; the live query brings the new
 * value back into the StateFlow. So the database is the single source of truth.
 */
object RulesRepository {

    // Our own "goroutine pool" for database work. SupervisorJob: one failed job doesn't kill the others.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: DetoxDatabase

    private val _windows = MutableStateFlow<List<QuietWindow>>(emptyList())
    val windows: StateFlow<List<QuietWindow>> = _windows.asStateFlow()

    private val _apps = MutableStateFlow<List<PausedApp>>(emptyList())
    val apps: StateFlow<List<PausedApp>> = _apps.asStateFlow()

    private val _limits = MutableStateFlow(LimitSettings())
    val limits: StateFlow<LimitSettings> = _limits.asStateFlow()

    private val _strict = MutableStateFlow(StrictSettings())
    val strict: StateFlow<StrictSettings> = _strict.asStateFlow()

    // v5: strict home screen. Looks (theme, quote...) and per-app choices (favorite/hidden/mindful).
    private val _homePrefs = MutableStateFlow(HomePrefs())
    val homePrefs: StateFlow<HomePrefs> = _homePrefs.asStateFlow()

    private val _appPrefs = MutableStateFlow<List<AppPref>>(emptyList())
    val appPrefs: StateFlow<List<AppPref>> = _appPrefs.asStateFlow()

    /** Page 1 of the strict home has room for this many favorites. */
    const val MAX_FAVORITES = Favorites.MAX

    /** Called once from DetoxApplication.onCreate (before any screen or the service starts). */
    fun init(context: Context) {
        if (::db.isInitialized) return
        db = DetoxDatabase.create(context.applicationContext)
        scope.launch {
            db.quietWindowDao().observeAll().collect { rows -> _windows.value = rows.map { it.toDomain() } }
        }
        scope.launch {
            db.pausedAppDao().observeAll().collect { rows -> _apps.value = rows.map { it.toDomain() } }
        }
        scope.launch {
            db.settingsDao().observe().collect { row ->
                _limits.value = row?.toDomain() ?: LimitSettings()
                _strict.value = row?.toStrict() ?: StrictSettings()
            }
        }
        scope.launch {
            db.homePrefsDao().observe().collect { row -> _homePrefs.value = row?.toDomain() ?: HomePrefs() }
        }
        scope.launch {
            db.appPrefDao().observeAll().collect { rows -> _appPrefs.value = rows.map { it.toDomain() } }
        }
    }

    // ---- v5: strict home ------------------------------------------------------------

    /** Save all the looks at once (only the Settings screen edits them, so no lost updates). */
    fun saveHomePrefs(prefs: HomePrefs) {
        scope.launch { db.homePrefsDao().save(prefs.toEntity()) }
    }

    /**
     * Put an app on page 1 (at the end of the favorites).
     * suspend + a Boolean answer, because the screen must know if it worked:
     * false = already [MAX_FAVORITES] favorites, so show "Page 1 is full".
     */
    suspend fun addFavorite(packageName: String): Boolean = withContext(Dispatchers.IO) {
        db.appPrefDao().addFavorite(packageName, MAX_FAVORITES)
    }

    /** Take an app off page 1; the others move up to close the gap. */
    fun removeFavorite(packageName: String) {
        scope.launch { db.appPrefDao().removeFavorite(packageName) }
    }

    /** Hide / show an app in the strict home lists. It stays installed and usable. */
    fun setHidden(packageName: String, hidden: Boolean) {
        scope.launch { db.appPrefDao().setHidden(packageName, hidden) }
    }

    /** Mindful = this app opens only after a 5-second "Take a breath" pause. */
    fun setMindful(packageName: String, mindful: Boolean) {
        scope.launch { db.appPrefDao().setMindful(packageName, mindful) }
    }

    fun saveLimits(limits: LimitSettings) {
        scope.launch {
            db.settingsDao().updateLimits(limits.dailyLimitOn, limits.dailyLimitMin, DbCodec.minutesOf(limits.dayStart))
        }
    }

    fun setStrictMode(on: Boolean) {
        scope.launch { db.settingsDao().setStrictMode(on) }
    }

    fun setCountdown(seconds: Int) {
        scope.launch { db.settingsDao().setCountdown(seconds) }
    }

    /**
     * false = you switched protection off INSIDE Detox (after the countdown), so Detox must
     * not switch it back on. true = you want it on (set again whenever the service starts).
     */
    fun setProtectionWanted(wanted: Boolean) {
        scope.launch { db.settingsDao().setProtectionWanted(wanted) }
    }

    /**
     * Direct one-time read, for the home screen: when Home is pressed right after the phone
     * starts, the live StateFlow may not have loaded yet, so we ask the database itself.
     */
    suspend fun readStrictModeOn(): Boolean = withContext(Dispatchers.IO) {
        db.settingsDao().get()?.strictModeOn ?: false
    }

    /** Same idea as [readStrictModeOn], but the whole strict/protection settings row. */
    suspend fun readStrict(): StrictSettings = withContext(Dispatchers.IO) {
        db.settingsDao().get()?.toStrict() ?: StrictSettings()
    }

    /** minutes = null removes the app's own limit. */
    fun setAppLimit(packageName: String, minutes: Int?) {
        scope.launch { db.pausedAppDao().setLimit(packageName, minutes) }
    }

    fun setWindowEnabled(id: Int, enabled: Boolean) {
        scope.launch { db.quietWindowDao().setEnabled(id, enabled) }
    }

    /** New window (id = 0) -> INSERT; existing -> UPDATE. */
    fun saveWindow(window: QuietWindow) {
        scope.launch { db.quietWindowDao().upsert(window.toEntity()) }
    }

    fun deleteWindow(id: Int) {
        scope.launch { db.quietWindowDao().delete(id) }
    }

    fun setAppPaused(packageName: String, label: String, paused: Boolean) {
        scope.launch {
            if (paused) {
                db.pausedAppDao().insert(PausedAppEntity(packageName, label, System.currentTimeMillis()))
            } else {
                db.pausedAppDao().delete(packageName)
            }
        }
    }
}
