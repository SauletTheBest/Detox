package com.detox.app.domain

/**
 * Your personal choices for ONE app on the strict home screen. One row in `app_pref`.
 *
 * favoritePos = its place among the (max 5) favorites on page 1: 0, 1, 2 ... null = not a favorite.
 * hidden      = not shown in the app list (it's still installed and still opens from elsewhere).
 * mindful     = opens only after a 5-second "Take a breath" pause (see MindfulGate).
 *
 * Apps you never touched have no row at all: "no row" means all defaults.
 */
data class AppPref(
    val packageName: String,
    val favoritePos: Int? = null,
    val hidden: Boolean = false,
    val mindful: Boolean = false,
)

/** Small pure helpers for the favorites list, so the rules can be unit-tested on the PC. */
object Favorites {

    /** Page 1 has room for 5 apps. Empty slots are filled with recent apps by the home screen. */
    const val MAX = 5

    /** Favorite package names in their order on page 1 (position 0 first). */
    fun ordered(prefs: List<AppPref>): List<String> =
        prefs.filter { it.favoritePos != null }
            .sortedBy { it.favoritePos }
            .map { it.packageName }

    /**
     * After removing one favorite there's a gap (0, 2, 3). Give everyone new compact positions
     * in the same order: (0, 1, 2). Like re-numbering a queue after someone leaves it.
     */
    fun renumber(inOrder: List<String>): Map<String, Int> =
        inOrder.withIndex().associate { (index, pkg) -> pkg to index }
}
