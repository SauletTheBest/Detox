package com.detox.app.data

import com.detox.app.data.db.DetoxDatabase
import com.detox.app.data.db.OwnQuoteEntity
import com.detox.app.data.db.toDomain
import com.detox.app.domain.BuiltinQuotePref
import com.detox.app.domain.OwnQuote
import com.detox.app.domain.QuoteBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Your quotes, live from the database (same idea as RulesRepository):
 *   own_quote / builtin_quote_pref --(Room live query)--> StateFlow --> Quotes screen + Detox Home
 * The built-in quotes themselves are in code (ui/launcher/Quotes.kt); QuoteBook mixes the two.
 */
object QuotesRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: DetoxDatabase

    private val _own = MutableStateFlow<List<OwnQuote>>(emptyList())
    val own: StateFlow<List<OwnQuote>> = _own.asStateFlow()

    /** Built-in quote text -> your choice. Only quotes you touched are in the map. */
    private val _builtinPrefs = MutableStateFlow<Map<String, BuiltinQuotePref>>(emptyMap())
    val builtinPrefs: StateFlow<Map<String, BuiltinQuotePref>> = _builtinPrefs.asStateFlow()

    /** Called from RulesRepository.init (same database file). */
    fun init(database: DetoxDatabase) {
        if (::db.isInitialized) return
        db = database
        scope.launch { db.quotesDao().observeOwn().collect { rows -> _own.value = rows.map { it.toDomain() } } }
        scope.launch {
            db.quotesDao().observeBuiltinPrefs().collect { rows ->
                _builtinPrefs.value = rows.associate { it.quoteText to it.toDomain() }
            }
        }
    }

    /** Save a new quote of yours. Blank text is ignored; long text is cut to the limits. */
    fun addOwn(text: String, author: String?) {
        val cleanText = text.trim().take(QuoteBook.MAX_TEXT)
        if (cleanText.isEmpty()) return
        val cleanAuthor = author?.trim()?.take(QuoteBook.MAX_AUTHOR)?.ifEmpty { null }
        scope.launch {
            db.quotesDao().insertOwn(
                OwnQuoteEntity(quoteText = cleanText, author = cleanAuthor, createdAt = System.currentTimeMillis())
            )
        }
    }

    fun deleteOwn(id: Long) {
        scope.launch { db.quotesDao().deleteOwn(id) }
    }

    fun setOwnLiked(id: Long, liked: Boolean) {
        scope.launch { db.quotesDao().setOwnLiked(id, liked) }
    }

    fun setBuiltinLiked(text: String, liked: Boolean) {
        scope.launch { db.quotesDao().setBuiltinLiked(text, liked) }
    }

    fun setBuiltinHidden(text: String, hidden: Boolean) {
        scope.launch { db.quotesDao().setBuiltinHidden(text, hidden) }
    }
}
