package euchre.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val feltLight = Color(0xFF2B7A52)
    val felt = Color(0xFF1F5E3E)
    val feltDark = Color(0xFF113726)
    val panel = Color(0xFF0E2A1D)
    val panelLine = Color(0x33FFFFFF)
    val cream = Color(0xFFFFFCF4)
    val cardEdge = Color(0x26000000)
    val red = Color(0xFFC0392B)
    val ink = Color(0xFF1C1C1C)
    val gold = Color(0xFFEBC66E)
    val goldSoft = Color(0x55EBC66E)
    val text = Color(0xFFF4F1E8)
    val textDim = Color(0xB3F4F1E8)
    val backA = Color(0xFF7A1F2B)
    val backB = Color(0xFF4E1019)
}

val Serif = FontFamily.Serif

@Composable
fun EuchreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.gold,
            onPrimary = Color(0xFF2A1F05),
            secondary = Palette.feltLight,
            onSecondary = Palette.text,
            background = Palette.feltDark,
            onBackground = Palette.text,
            surface = Palette.panel,
            onSurface = Palette.text,
            surfaceVariant = Color(0xFF1A3D2C),
            onSurfaceVariant = Palette.textDim,
            outline = Palette.panelLine,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp),
            headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        ),
        content = content,
    )
}
