package com.nexusforge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's mark, drawn in code so it matches the launcher icon exactly (same N, same spark,
 * same coordinates on a 108-unit grid) and scales crisply anywhere in the UI.
 */
@Composable
fun NexusMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 108f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF1C1F27), Color(0xFF0B0D12))),
            cornerRadius = CornerRadius(s * 0.27f)
        )
        withTransform({ scale(1.45f, 1.45f, pivot = Offset(s / 2f, s / 2f)) }) {
            val n = Path().apply {
                moveTo(36 * u, 38 * u); lineTo(46 * u, 38 * u); lineTo(58 * u, 58 * u); lineTo(58 * u, 38 * u)
                lineTo(66 * u, 38 * u); lineTo(66 * u, 74 * u); lineTo(56 * u, 74 * u); lineTo(44 * u, 54 * u)
                lineTo(44 * u, 74 * u); lineTo(36 * u, 74 * u); close()
            }
            drawPath(
                n,
                Brush.linearGradient(
                    listOf(Color(0xFFFFB35C), Color(0xFFF97316)),
                    start = Offset(36 * u, 38 * u), end = Offset(66 * u, 74 * u)
                )
            )
            val star = Path().apply {
                moveTo(74 * u, 28 * u); lineTo(76 * u, 33 * u); lineTo(81 * u, 35 * u); lineTo(76 * u, 37 * u)
                lineTo(74 * u, 42 * u); lineTo(72 * u, 37 * u); lineTo(67 * u, 35 * u); lineTo(72 * u, 33 * u); close()
            }
            drawPath(star, Color(0xFFFFE2B8))
        }
    }
}
