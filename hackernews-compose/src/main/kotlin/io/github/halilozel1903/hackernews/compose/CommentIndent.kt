package io.github.halilozel1903.hackernews.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Thread line colors that cycle by depth, readable on light and dark backgrounds. */
public val DefaultThreadColors: List<Color> = listOf(
    Color(0xFFFF6600),
    Color(0xFF3D8BFD),
    Color(0xFF2EB67D),
    Color(0xFFB57EDC),
    Color(0xFFE0A100),
    Color(0xFFE5547A),
)

/**
 * Indents a comment row by [depth] and draws one vertical thread line per level, like threaded readers do. Lines
 * mirror in right-to-left layouts. Deep threads stop indenting after [maxLevels] so text keeps its width.
 */
public fun Modifier.commentIndent(
    depth: Int,
    colors: List<Color> = DefaultThreadColors,
    step: Dp = 12.dp,
    lineWidth: Dp = 2.dp,
    maxLevels: Int = 8,
): Modifier {
    val levels = depth.coerceIn(0, maxLevels)
    if (levels == 0) return this
    return this
        .drawBehind {
            val stepPx = step.toPx()
            val width = lineWidth.toPx()
            for (level in 0 until levels) {
                val x = level * stepPx + stepPx / 2
                val drawX = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                val color = if (colors.isEmpty()) Color.Gray else colors[(depth - levels + level) % colors.size]
                drawLine(
                    color = color.copy(alpha = color.alpha * 0.7f),
                    start = Offset(drawX, 0f),
                    end = Offset(drawX, size.height),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
        .padding(start = step * levels)
}
