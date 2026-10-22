package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.ui.theme.Peridot
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val LogoBrush = listOf(Color(0xFFD4F3EA), Color(0xFF8FE0C8), Color(0xFF5CC9A8))

/**
 * The Peridot logo: four dots in a square. With [thinking] the dots pulse one after another,
 * which is how the app shows that an answer is on its way.
 */
@Composable
fun PeridotLogo(size: Dp, modifier: Modifier = Modifier, thinking: Boolean = false) {
    val phase = if (thinking) {
        val transition = rememberInfiniteTransition(label = "thinking")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
            label = "phase",
        )
        value
    } else {
        -1f
    }
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 512f
        val brush = Brush.verticalGradient(LogoBrush, startY = 100 * unit, endY = 412 * unit)
        // Clockwise, so the pulse goes round
        val centers = listOf(Offset(156f, 156f), Offset(356f, 156f), Offset(356f, 356f), Offset(156f, 356f))
        centers.forEachIndexed { i, c ->
            val alpha = if (phase < 0) 1f else {
                val d = ((phase - i) % 4f + 4f) % 4f
                if (d < 1f) 1f - 0.65f * d else 0.35f
            }
            drawCircle(brush, radius = 56 * unit, center = Offset(c.x * unit, c.y * unit), alpha = alpha)
        }
    }
}

@Composable
fun Avatar(name: String, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).background(Peridot.Mint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Peridot.OnMint,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed: nothing sensible to do
    }
}

private val dbTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** "5 minutes ago" for a database time ("YYYY-MM-DD HH:MM:SS", UTC), in the phone's language. */
fun ago(value: String?): String {
    if (value == null) return ""
    val millis = runCatching { LocalDateTime.parse(value, dbTime).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() ?: return ""
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(minOf(millis, now), now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

@Composable
fun planName(plan: String?, unlimited: Boolean): String = when {
    unlimited -> stringResource(R.string.plan_unlimited)
    plan == "plus" -> "Plus"
    plan == "extended" -> "Extended 5x"
    plan == "extended10" -> "Extended 10x"
    else -> stringResource(R.string.plan_free)
}

/** Returns a function that puts text on the clipboard. */
@Composable
fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Peridot", text))) } }
    }
}
