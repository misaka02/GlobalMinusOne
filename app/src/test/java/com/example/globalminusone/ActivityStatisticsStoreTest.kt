package com.example.globalminusone

import com.example.globalminusone.data.ActivityStatisticsStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityStatisticsStoreTest {
    @Test fun countsPersistAcrossInstancesAndResetOnLocalDateChange() {
        val prefs = MemoryPreferences()
        var day = LocalDate.of(2026, 11, 13)
        val store = ActivityStatisticsStore(prefs) { day }
        store.recordNotification()
        store.recordNotification()
        store.recordUnlock()
        assertEquals(2, store.counts.value.notifications)
        assertEquals(1, store.counts.value.unlocks)

        val restarted = ActivityStatisticsStore(prefs) { day }
        assertEquals(store.counts.value, restarted.counts.value)
        day = day.plusDays(1)
        assertEquals(0, restarted.refresh().notifications)
        assertEquals(0, restarted.counts.value.unlocks)
        restarted.recordUnlock()
        assertEquals(1, ActivityStatisticsStore(prefs) { day }.counts.value.unlocks)
        assertEquals(0, ActivityStatisticsStore(prefs) { day.minusDays(1) }.counts.value.unlocks)
    }

    @Test fun corruptedNegativeCountsAreNormalized() {
        val day = LocalDate.of(2026, 11, 13)
        val prefs = MemoryPreferences(mapOf(
            "date" to day.toString(), "notifications" to -9, "unlocks" to -2
        ))
        val store = ActivityStatisticsStore(prefs) { day }
        assertEquals(0, store.counts.value.notifications)
        assertEquals(0, store.counts.value.unlocks)
        store.recordNotification()
        assertEquals(1, ActivityStatisticsStore(prefs) { day }.counts.value.notifications)
    }
}
