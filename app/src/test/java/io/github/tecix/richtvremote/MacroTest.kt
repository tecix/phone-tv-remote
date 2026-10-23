package io.github.tecix.richtvremote

import io.github.tecix.richtvremote.ui.screens.formatUptime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroTest {
    @Test
    fun parsesAliasesAndRepeats() {
        assertEquals(
            listOf(TvKey.DPAD_DOWN, TvKey.DPAD_RIGHT, TvKey.DPAD_RIGHT, TvKey.DPAD_CENTER, TvKey.BACK),
            TvKey.parseMacro("down right*2 OK, BACK"),
        )
        assertEquals(listOf(TvKey.MENU), TvKey.parseMacro("MENU"))
    }

    @Test
    fun rejectsUnknownOrEmpty() {
        assertNull(TvKey.parseMacro("DOWN JUMP"))
        assertNull(TvKey.parseMacro("RIGHT*0"))
        assertNull(TvKey.parseMacro("   "))
    }

    @Test
    fun formatsUptime() {
        assertEquals("3h 53m", formatUptime(13_984_590))
        assertEquals("2d 4h", formatUptime((2 * 24 + 4) * 3_600_000L + 5 * 60_000L))
        assertEquals("0m", formatUptime(-5))
    }
}
