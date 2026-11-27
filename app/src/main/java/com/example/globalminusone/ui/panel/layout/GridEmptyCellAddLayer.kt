package com.example.globalminusone.ui.panel.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.policy.EmptyGridCell
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.PanelPalette

@Composable
internal fun GridEmptyCellAddLayer(
    emptyCells: List<EmptyGridCell>,
    palette: PanelPalette,
    onAddAtCell: (col: Int, row: Int) -> Unit
) {
    emptyCells.forEach { cell ->
        key(cell.row * 32 + cell.col) {
            Surface(
                onClick = { onAddAtCell(cell.col, cell.row) },
                modifier = Modifier
                    .offset(x = cell.xDp.dp, y = cell.yDp.dp)
                    .size(width = cell.widthDp.dp, height = cell.heightDp.dp)
                    .semantics { contentDescription = "在网格第${cell.row + 1}行第${cell.col + 1}列添加卡片" },
                shape = RoundedCornerShape(8.dp),
                color = palette.accent.copy(alpha = 0.045f),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.24f))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = palette.accent.copy(alpha = 0.78f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
