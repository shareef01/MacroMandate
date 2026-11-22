package com.sharek.macromandate.ui

import com.sharek.macromandate.ui.theme.TerminalTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalThemeTest {

    @Test
    fun fromIdReturnsCorrectThemeForKnownIds() {
        assertEquals(TerminalTheme.CYBER_CYAN, TerminalTheme.fromId("cyber_cyan"))
        assertEquals(TerminalTheme.MATRIX_GREEN, TerminalTheme.fromId("matrix_green"))
        assertEquals(TerminalTheme.AMBER_CRT, TerminalTheme.fromId("amber_crt"))
        assertEquals(TerminalTheme.STARK_MONO, TerminalTheme.fromId("stark_mono"))
    }

    @Test
    fun fromIdFallsBackToCyberCyanOnNullOrUnknownId() {
        assertEquals(TerminalTheme.CYBER_CYAN, TerminalTheme.fromId(null))
        assertEquals(TerminalTheme.CYBER_CYAN, TerminalTheme.fromId(""))
        assertEquals(TerminalTheme.CYBER_CYAN, TerminalTheme.fromId("non_existent_theme"))
    }

    @Test
    fun allThemesHaveNonBlankDisplayNamesAndTaglines() {
        TerminalTheme.entries.forEach { theme ->
            org.junit.Assert.assertTrue(theme.displayName.isNotBlank())
            org.junit.Assert.assertTrue(theme.tagline.isNotBlank())
            org.junit.Assert.assertTrue(theme.id.isNotBlank())
        }
    }
}
