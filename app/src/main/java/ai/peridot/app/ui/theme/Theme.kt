package ai.peridot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The website's palette (dark only, like tryperidot.io). */
object Peridot {
    val Background = Color(0xFF141413)
    val Surface = Color(0xFF1B1A19)
    val SurfaceHigh = Color(0xFF262523)
    val Border = Color(0x1AE8E6E0)
    val Text = Color(0xFFE8E6E0)
    val Muted = Color(0xFF8C8A83)
    val Mint = Color(0xFF6FD3B7)
    val MintLight = Color(0xFF8FE0C8)
    val OnMint = Color(0xFF0F1D19)
    val Error = Color(0xFFE5866F)
    val Code = Color(0xFF0F0F0E)
}

private val colors = darkColorScheme(
    primary = Peridot.Mint,
    onPrimary = Peridot.OnMint,
    secondary = Peridot.MintLight,
    onSecondary = Peridot.OnMint,
    background = Peridot.Background,
    onBackground = Peridot.Text,
    surface = Peridot.Background,
    onSurface = Peridot.Text,
    surfaceVariant = Peridot.SurfaceHigh,
    onSurfaceVariant = Peridot.Muted,
    surfaceContainerLowest = Peridot.Background,
    surfaceContainerLow = Peridot.Surface,
    surfaceContainer = Peridot.Surface,
    surfaceContainerHigh = Peridot.Surface,
    surfaceContainerHighest = Peridot.SurfaceHigh,
    outline = Peridot.Border,
    outlineVariant = Peridot.Border,
    error = Peridot.Error,
    onError = Peridot.OnMint,
)

private val base = Typography()

private val typography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

@Composable
fun PeridotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
