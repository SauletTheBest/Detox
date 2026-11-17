package com.detox.app.domain

import java.time.LocalDate

/** A quote's words. author = null for Detox's own lines and usually for yours. */
data class QuoteText(val text: String, val author: String? = null)

/** One quote YOU wrote (table own_quote). */
data class OwnQuote(val id: Long, val text: String, val author: String?, val liked: Boolean)

/** Your choice about one built-in quote (table builtin_quote_pref). No row = not liked, not hidden. */
data class BuiltinQuotePref(val liked: Boolean = false, val hidden: Boolean = false)

/**
 * One quote as the Quotes screen shows it: built-in or yours, with your ♥ / hidden choices.
 * ownId = null -> built-in (it can be hidden); ownId != null -> yours (it can be deleted).
 */
data class QuoteItem(
    val text: String,
    val author: String?,
    val ownId: Long?,
    val liked: Boolean,
    val hidden: Boolean,
) {
    val isOwn: Boolean get() = ownId != null
}

/**
 * Quotes, pure Kotlin (tested in QuoteBookTest).
 *
 *   built-in list (code) + your choices (DB) + your own quotes (DB) --merge--> all items
 *   all items --pool--> what Detox Home may show --forDay--> today's quote
 */
object QuoteBook {

    /** Your own quotes first (newest first), then the built-in ones in their usual order. */
    fun merge(builtIns: List<QuoteText>, prefs: Map<String, BuiltinQuotePref>, own: List<OwnQuote>): List<QuoteItem> {
        val mine = own.sortedByDescending { it.id }.map { QuoteItem(it.text, it.author, it.id, it.liked, hidden = false) }
        val builtIn = builtIns.map { q ->
            val pref = prefs[q.text] ?: BuiltinQuotePref()     // the text is the key: stable across updates
            QuoteItem(q.text, q.author, ownId = null, liked = pref.liked, hidden = pref.hidden)
        }
        return mine + builtIn
    }

    /**
     * What Detox Home may show: never a hidden one. onlyLiked = just your ♥ ones and your own.
     * If that leaves nothing (you hid everything, or liked nothing yet), fall back to all built-in
     * quotes: an empty quote line would look broken, and "no quotes" has its own switch in Settings.
     */
    fun pool(items: List<QuoteItem>, onlyLiked: Boolean): List<QuoteItem> {
        val visible = items.filter { !it.hidden }
        val chosen = if (onlyLiked) visible.filter { it.liked || it.isOwn } else visible
        return chosen.ifEmpty { items.filter { !it.isOwn } }
    }

    /**
     * Today's quote. Every day the next one: the day number (days since 1970) picks it.
     * [offset] = how many times you tapped "next" today. null only for an empty pool.
     */
    fun forDay(pool: List<QuoteItem>, day: LocalDate, offset: Int = 0): QuoteItem? {
        if (pool.isEmpty()) return null
        return pool[Math.floorMod(day.toEpochDay() + offset, pool.size.toLong()).toInt()]
    }

    /** Limits for your own quotes (one calm line, not an essay). */
    const val MAX_TEXT = 200
    const val MAX_AUTHOR = 40
}
