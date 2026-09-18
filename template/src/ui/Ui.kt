@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package __PKG__.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ───────────── Cards ───────────── */

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

/** Gradient hero with a soft expressive blob in the corner. */
@Composable
fun HeroCard(
    colors: List<Color>,
    blobShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    blobTint: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Brush.linearGradient(colors)),
    ) {
        Box(
            Modifier.size(200.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-70).dp)
                .clip(blobShape.toShape()).background(blobTint),
        )
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Small icon set inside an expressive shape — used for list leading icons and empty states. */
@Composable
fun ShapeIcon(icon: ImageVector, container: Color, tint: Color, shape: RoundedPolygon = MaterialShapes.Sunny, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(shape.toShape()).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, shape: RoundedPolygon = MaterialShapes.Clover8Leaf) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShapeIcon(icon, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, shape, 72)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified) {
    val keyColor = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor.copy(alpha = 0.75f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = keyColor)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** Big number that counts up/down when its value changes. */
@Composable
fun AnimatedNumber(value: Int, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified, suffix: String = "") {
    val v by animateIntAsState(value, tween(700), label = "num")
    Text(String.format(Locale.US, "%,d", v) + suffix, style = style, color = color)
}

@Composable
fun animatedProgress(target: Float): Float {
    val p by animateFloatAsState(target.coerceIn(0f, 1f), tween(900), label = "progress")
    return p
}

/* ───────────── Charts ───────────── */

/** Smooth line chart of [values] with a gradient fill; the final point is highlighted. */
@Composable
fun TrendChart(values: List<Float>, modifier: Modifier = Modifier, line: Color = MaterialTheme.colorScheme.primary, target: Float? = null) {
    val fill = line.copy(alpha = 0.18f)
    val targetColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.size < 2) return@Canvas
        val lo = minOf(values.min(), target ?: values.min()); val hi = maxOf(values.max(), target ?: values.max())
        val span = (hi - lo).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        fun pt(i: Int) = Offset(i * stepX, size.height - ((values[i] - lo) / span) * (size.height - 12f) - 6f)
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) {
                val p0 = pt(i - 1); val p1 = pt(i); val mid = (p0.x + p1.x) / 2
                cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(fill, Color.Transparent)))
        target?.let {
            val y = size.height - ((it - lo) / span) * (size.height - 12f) - 6f
            drawLine(targetColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
        drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = pt(values.lastIndex)
        drawCircle(line, 9f, last); drawCircle(Color.White, 4f, last)
    }
}

/** Rounded bars; bars at/over [goal] are tinted [hit]. */
@Composable
fun BarChart(values: List<Pair<String, Int>>, goal: Int, modifier: Modifier = Modifier, base: Color = MaterialTheme.colorScheme.tertiaryContainer, hit: Color = MaterialTheme.colorScheme.tertiary) {
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            if (values.isEmpty()) return@Canvas
            val maxV = maxOf(goal, values.maxOf { it.second }).toFloat()
            val gap = 10f; val w = (size.width - gap * (values.size - 1)) / values.size
            val goalY = size.height - (goal / maxV) * size.height
            drawLine(label.copy(alpha = 0.4f), Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            values.forEachIndexed { i, (_, v) ->
                val h = maxOf(8f, (v / maxV) * size.height)
                drawRoundRect(if (v >= goal) hit else base, Offset(i * (w + gap), size.height - h), androidx.compose.ui.geometry.Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { Text(it.first, style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
    }
}

/* ───────────── Formatting ───────────── */

fun Int.kcal(): String = String.format(Locale.US, "%,d kcal", this)
fun Int.signedKcal(): String = (if (this > 0) "+" else "") + String.format(Locale.US, "%,d", this)
fun Double.kg1(): String = String.format(Locale.US, "%.1f kg", this)
fun LocalDate.pretty(): String = format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
fun String.prettyDate(): String = runCatching { LocalDate.parse(this).format(DateTimeFormatter.ofPattern("EEE, d MMM")) }.getOrDefault(this)
fun String.dayShort(): String = runCatching { LocalDate.parse(this).format(DateTimeFormatter.ofPattern("EEE")) }.getOrDefault(takeLast(2))
