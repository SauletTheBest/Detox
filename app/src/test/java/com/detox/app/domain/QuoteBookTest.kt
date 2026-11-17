package com.detox.app.domain

import com.detox.app.ui.launcher.Quotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Quotes: merging your choices, what Detox Home may show, and the quote of the day. */
class QuoteBookTest {

    private val builtIns = listOf(QuoteText("Rest."), QuoteText("Look up."), QuoteText("Simplicity!", "Thoreau"))
    private val day = LocalDate.of(2026, 11, 17)

    @Test fun `own quotes come first, newest first, then built-ins in order`() {
        val own = listOf(OwnQuote(1, "Mine A", null, liked = false), OwnQuote(2, "Mine B", "Me", liked = true))
        val items = QuoteBook.merge(builtIns, emptyMap(), own)
        assertEquals(listOf("Mine B", "Mine A", "Rest.", "Look up.", "Simplicity!"), items.map { it.text })
        assertTrue(items[0].isOwn)
        assertFalse(items[2].isOwn)
    }

    @Test fun `choices are applied to built-in quotes by their text`() {
        val prefs = mapOf("Look up." to BuiltinQuotePref(liked = true), "Rest." to BuiltinQuotePref(hidden = true))
        val items = QuoteBook.merge(builtIns, prefs, emptyList()).associateBy { it.text }
        assertTrue(items.getValue("Look up.").liked)
        assertTrue(items.getValue("Rest.").hidden)
        assertFalse(items.getValue("Simplicity!").liked)
    }

    @Test fun `hidden quotes are never shown`() {
        val items = QuoteBook.merge(builtIns, mapOf("Rest." to BuiltinQuotePref(hidden = true)), emptyList())
        assertEquals(listOf("Look up.", "Simplicity!"), QuoteBook.pool(items, onlyLiked = false).map { it.text })
    }

    @Test fun `only liked keeps your hearts and your own quotes`() {
        val items = QuoteBook.merge(
            builtIns,
            mapOf("Simplicity!" to BuiltinQuotePref(liked = true)),
            listOf(OwnQuote(5, "Mine", null, liked = false)),
        )
        assertEquals(listOf("Mine", "Simplicity!"), QuoteBook.pool(items, onlyLiked = true).map { it.text })
    }

    @Test fun `an empty choice falls back to the built-in quotes`() {
        // "Only liked" but nothing liked yet -> still a quote on the home screen.
        val none = QuoteBook.merge(builtIns, emptyMap(), emptyList())
        assertEquals(3, QuoteBook.pool(none, onlyLiked = true).size)
        // Everything hidden -> fall back too (the Settings switch is the way to turn quotes off).
        val allHidden = QuoteBook.merge(builtIns, builtIns.associate { it.text to BuiltinQuotePref(hidden = true) }, emptyList())
        assertEquals(3, QuoteBook.pool(allHidden, onlyLiked = false).size)
    }

    @Test fun `quote of the day is the same all day and taps move on`() {
        val pool = QuoteBook.pool(QuoteBook.merge(builtIns, emptyMap(), emptyList()), onlyLiked = false)
        assertEquals(QuoteBook.forDay(pool, day), QuoteBook.forDay(pool, day))
        assertEquals(QuoteBook.forDay(pool, day.plusDays(1)), QuoteBook.forDay(pool, day, offset = 1))
        // After a full round of taps you're back at the first one.
        assertEquals(QuoteBook.forDay(pool, day), QuoteBook.forDay(pool, day, offset = pool.size))
    }

    @Test fun `empty pool gives no quote instead of crashing`() {
        assertNull(QuoteBook.forDay(emptyList(), day))
    }

    @Test fun `built-in quote texts are unique (the text is their key)`() {
        // The database and the Quotes list both use the text as the key: a duplicate would mix up
        // two quotes' ♥ / hidden, and crash the list (two items with the same key).
        val texts = Quotes.all.map { it.text }
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test fun `built-in quotes fit the same limits as your own`() {
        assertTrue(Quotes.all.all { it.text.length <= QuoteBook.MAX_TEXT })
    }
}
