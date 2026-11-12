package com.detox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The string Android keeps for "enabled accessibility services" (and the shortcut lists).
 * The real phone has Ella Translate in it too, which must never be touched.
 */
class ComponentListTest {

    private val ella = "com.transsion.mol/.service.as.ASService"
    private val detoxFull = "com.detox.app/com.detox.app.AppWatcherService"
    private val detoxShort = "com.detox.app/.AppWatcherService"

    @Test fun `short and full spelling are the same component`() =
        assertTrue(ComponentList.contains(detoxShort, detoxFull))

    @Test fun `adding keeps the other services`() =
        assertEquals("$ella:$detoxFull", ComponentList.with(ella, detoxFull))

    @Test fun `adding to an empty setting`() {
        assertEquals(detoxFull, ComponentList.with(null, detoxFull))
        assertEquals(detoxFull, ComponentList.with("", detoxFull))
    }

    @Test fun `adding twice does not duplicate`() =
        assertEquals("$ella:$detoxShort", ComponentList.with("$ella:$detoxShort", detoxFull))

    @Test fun `removing takes out only Detox, in either spelling`() {
        assertEquals(ella, ComponentList.without("$ella:$detoxShort", detoxFull))
        assertEquals(ella, ComponentList.without("$detoxFull:$ella", detoxFull))
    }

    @Test fun `removing the last one leaves an empty string`() =
        assertEquals("", ComponentList.without(detoxFull, detoxFull))

    @Test fun `not there`() =
        assertFalse(ComponentList.contains(ella, detoxFull))
}
