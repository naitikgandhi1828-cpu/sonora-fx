package com.sonora.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

/** A page: optional top bar (orange back arrow, centred title) over scrolling content. */
@Composable
fun Screen(
    title: String?,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Sx.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (title != null) TopBar(title, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Sx.Accent, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            color = Sx.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SxCard(
    modifier: Modifier = Modifier,
    padding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Sx.Card)
            .padding(padding),
        content = content,
    )
}

/** Card holding full-width rows (no inner padding). */
@Composable
fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sx.Card),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        color = Sx.Sub, fontSize = 15.sp, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp),
    )
}

@Composable
fun CardLabel(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        color = Sx.Sub, fontSize = 15.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Sx.Sub, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier)
}

@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Sx.Accent else Sx.Sub, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Sx.Text else Sx.Sub, fontSize = 19.sp)
            if (subtitle != null) Text(subtitle, color = Sx.Sub, fontSize = 15.sp, lineHeight = 19.sp)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = Sx.Sub, fontSize = 18.sp)
        }
        if (enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Sx.Sub, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(Sx.Divider))
}

@Composable
fun InfoRow(icon: ImageVector, text: String, tint: Color = Sx.Sub) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, color = Sx.Sub, fontSize = 16.sp, lineHeight = 21.sp)
    }
}

@Composable
fun SxSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Sx.Accent,
            checkedBorderColor = Sx.Accent,
            uncheckedThumbColor = Sx.Sub,
            uncheckedTrackColor = Sx.Tile,
            uncheckedBorderColor = Sx.Track,
        ),
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Sx.Text else Sx.Sub, fontSize = 19.sp)
            if (subtitle != null) Text(subtitle, color = Sx.Sub, fontSize = 15.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        SxSwitch(checked, enabled, onChange)
    }
}

/** Label, orange monospace value, reset arrow, and a thin slider underneath. */
@Composable
fun ValueSlider(
    label: String,
    valueText: String,
    fraction: Float,
    onFraction: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, color = Sx.Accent, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Replay, "Reset", tint = Sx.Sub,
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onReset).padding(5.dp),
            )
        }
        SxSlider(fraction, onFraction, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** Thin orange-filled track with a large white thumb, like Sonora FX. */
@Composable
fun SxSlider(fraction: Float, onFraction: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onFraction)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                val r = 18.dp.toPx()
                fun f(x: Float) = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                detectTapGestures { cb(f(it.x)) }
            }
            .pointerInput(Unit) {
                val r = 18.dp.toPx()
                fun f(x: Float) = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    cb(f(change.position.x))
                }
            }
    ) {
        val r = 18.dp.toPx()
        val y = size.height / 2
        val left = r
        val right = size.width - r
        val x = left + (right - left) * fraction.coerceIn(0f, 1f)
        val w = 6.dp.toPx()
        drawLine(Sx.Track, Offset(left, y), Offset(right, y), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(Sx.Accent, Offset(left, y), Offset(x, y), strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(Color.White, r, Offset(x, y))
    }
}

/** Ten vertical band faders with frequency labels underneath. */
@Composable
fun VerticalEq(
    gains: List<Float>,
    labels: List<String>,
    active: Boolean,
    range: Float,
    onChange: (Int, Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        gains.forEachIndexed { i, g ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (g == 0f) "0" else String.format(Locale.US, "%+.1f", g),
                    color = if (active && g != 0f) Sx.Accent else Sx.Sub,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Fader(g, range, active) { onChange(i, it) }
                Spacer(Modifier.height(10.dp))
                Text(labels.getOrElse(i) { "" }, color = Sx.Sub, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Fader(gain: Float, range: Float, active: Boolean, onGain: (Float) -> Unit) {
    val cb by rememberUpdatedState(onGain)
    Canvas(
        Modifier
            .width(34.dp)
            .height(220.dp)
            .pointerInput(range) {
                val pad = 8.dp.toPx()
                fun g(y: Float): Float {
                    val f = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return ((f * 2f - 1f) * range * 2f).roundToInt() / 2f
                }
                detectTapGestures { cb(g(it.y)) }
            }
            .pointerInput(range) {
                val pad = 8.dp.toPx()
                fun g(y: Float): Float {
                    val f = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return ((f * 2f - 1f) * range * 2f).roundToInt() / 2f
                }
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    cb(g(change.position.y))
                }
            }
    ) {
        val pad = 8.dp.toPx()
        val cx = size.width / 2
        val top = pad
        val bottom = size.height - pad
        val mid = (top + bottom) / 2
        val y = mid - (gain / range).coerceIn(-1f, 1f) * (bottom - top) / 2
        val w = 6.dp.toPx()
        drawLine(Sx.Divider, Offset(cx, top), Offset(cx, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(Sx.Track, Offset(cx - 6.dp.toPx(), mid), Offset(cx + 6.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
        if (gain != 0f) {
            drawLine(if (active) Sx.Accent else Sx.Thumb, Offset(cx, mid), Offset(cx, y), strokeWidth = w, cap = StrokeCap.Round)
        }
        val tw = 22.dp.toPx(); val th = 12.dp.toPx()
        drawRoundRect(
            if (active) Color.White else Sx.Thumb,
            topLeft = Offset(cx - tw / 2, y - th / 2),
            size = Size(tw, th),
            cornerRadius = CornerRadius(th / 2, th / 2),
        )
    }
}

// --- value helpers -------------------------------------------------------

fun linFrac(v: Double, min: Double, max: Double): Float = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)
fun linVal(f: Float, min: Double, max: Double, step: Double): Double =
    Math.round((min + (max - min) * f) / step) * step

fun logFrac(v: Double, min: Double, max: Double): Float =
    (ln(v.coerceIn(min, max) / min) / ln(max / min)).toFloat().coerceIn(0f, 1f)
fun logVal(f: Float, min: Double, max: Double, step: Double): Double =
    (Math.round(min * exp(ln(max / min) * f) / step) * step).coerceIn(min, max)

fun fmtDb(v: Double): String = String.format(Locale.US, "%+.1f dB", v)
fun fmtHz(v: Double): String =
    if (v >= 1000) String.format(Locale.US, "%.1f kHz", v / 1000) else "${v.roundToInt()} Hz"
