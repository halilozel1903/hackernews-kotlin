package io.github.halilozel1903.hackernews.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small drawn icons, so the sample needs no icon library. */
@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.1f
        drawLine(color, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.82f, s * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.46f, s * 0.24f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.46f, s * 0.76f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** A speech bubble, for comment counts. */
@Composable
fun CommentIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.18f, s * 0.2f)
            lineTo(s * 0.82f, s * 0.2f)
            lineTo(s * 0.82f, s * 0.66f)
            lineTo(s * 0.44f, s * 0.66f)
            lineTo(s * 0.26f, s * 0.84f)
            lineTo(s * 0.26f, s * 0.66f)
            lineTo(s * 0.18f, s * 0.66f)
            close()
        }
        drawPath(path, color, style = Stroke(width = s * 0.09f, join = StrokeJoin.Round))
    }
}

/** An upward triangle, for points. */
@Composable
fun UpvoteIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.2f)
            lineTo(s * 0.84f, s * 0.76f)
            lineTo(s * 0.16f, s * 0.76f)
            close()
        }
        drawPath(path, color)
    }
}

/** The orange "Y" square of Hacker News. */
@Composable
fun HnLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.18f))
            .background(HnOrange),
        contentAlignment = Alignment.Center,
    ) {
        Text("Y", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.62f).sp)
    }
}
