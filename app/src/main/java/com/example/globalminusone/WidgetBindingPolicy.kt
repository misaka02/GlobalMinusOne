package com.example.globalminusone

/** A picker result may only transfer the ID allocated for that picker request. */
internal object WidgetBindingPolicy {
    fun ownedIdForResult(allocatedId: Int?, returnedId: Int?): Int? =
        allocatedId?.takeIf { it > 0 && (returnedId == null || returnedId == it) }

    fun idsToRelease(ownedIds: Collection<Int>, savedIds: Set<Int>): Set<Int> =
        ownedIds.filterTo(mutableSetOf()) { it > 0 && it !in savedIds }
}
