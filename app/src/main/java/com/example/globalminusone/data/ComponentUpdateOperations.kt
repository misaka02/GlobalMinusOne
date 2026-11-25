package com.example.globalminusone.data

/** Tree transformation and geometry bounds; callers retain their own exception and acceptance policy. */
internal object ComponentUpdateOperations {
    fun transform(components: List<ComponentItem>, componentId: String, columns: Int,
        transform: (ComponentItem) -> ComponentItem): List<ComponentItem> = components.transformComponent(componentId) { comp ->
        val transformed = transform(comp)
        val clampedW = transformed.w.coerceIn(1, columns)
        val clampedSpanX = if (transformed.gridSpanX >= GRID_SPAN_FULL_ROW) {
            GRID_SPAN_FULL_ROW
        } else {
            transformed.gridSpanX.coerceIn(0, 12)
        }
        transformed.copy(
            x = transformed.x.coerceIn(0, (columns - clampedW).coerceAtLeast(0)),
            y = transformed.y.coerceAtLeast(0),
            w = clampedW,
            h = transformed.h.coerceAtLeast(1),
            gridSpanX = clampedSpanX,
            gridSpanY = transformed.gridSpanY.coerceIn(0, 12),
            gridCol = transformed.gridCol.coerceIn(-1, 63),
            gridRow = transformed.gridRow.coerceIn(-1, 200)
        )
    }
}
