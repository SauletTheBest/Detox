package com.detox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Favorites on page 1 of the strict home: order and closing gaps. */
class FavoritesTest {

    @Test fun `ordered returns favorites by position and skips the rest`() {
        val prefs = listOf(
            AppPref("maps", favoritePos = 2),
            AppPref("telegram", favoritePos = 0),
            AppPref("chess", hidden = true),            // not a favorite
            AppPref("kaspi", favoritePos = 1),
            AppPref("youtube", mindful = true),         // not a favorite
        )
        assertEquals(listOf("telegram", "kaspi", "maps"), Favorites.ordered(prefs))
    }

    @Test fun `ordered of nothing is empty`() {
        assertEquals(emptyList<String>(), Favorites.ordered(emptyList()))
    }

    @Test fun `renumber closes the gap and keeps the order`() {
        // Positions were 0, 2, 3 after "kaspi" (1) was removed.
        val remaining = listOf("telegram", "maps", "gmail")
        assertEquals(mapOf("telegram" to 0, "maps" to 1, "gmail" to 2), Favorites.renumber(remaining))
    }

    @Test fun `renumber of an empty list is empty`() {
        assertEquals(emptyMap<String, Int>(), Favorites.renumber(emptyList()))
    }

    @Test fun `page 1 holds five favorites`() {
        assertEquals(5, Favorites.MAX)
    }
}
