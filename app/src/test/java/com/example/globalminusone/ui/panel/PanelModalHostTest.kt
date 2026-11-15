package com.example.globalminusone.ui.panel

import org.junit.Assert.*
import org.junit.Test

class PanelModalHostTest {
    @Test
    fun disposingOldModalDoesNotRemoveItsReplacement() {
        val state = PanelModalHostState()
        val oldOwner = Any()
        val newOwner = Any()
        state.show(PanelModalEntry(oldOwner, {}) {})
        state.show(PanelModalEntry(newOwner, {}) {})
        state.remove(oldOwner)
        assertSame(newOwner, state.entry?.owner)
        state.remove(newOwner)
        assertNull(state.entry)
    }

    @Test
    fun backOnlyConsumesWhenThereIsAnActiveModal() {
        val state = PanelModalHostState()
        val owner = Any()
        assertFalse(state.dismiss())
        var dismissed = false
        state.show(PanelModalEntry(owner, { dismissed = true; state.remove(owner) }) {})
        assertTrue(state.dismiss())
        assertTrue(dismissed)
        assertFalse(state.dismiss())
    }
}
