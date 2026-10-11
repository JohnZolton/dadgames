package dadgames.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Colours for one game. Every shared widget reads these through [LocalPalette]. */
data class GamePalette(
    val bgLight: Color,
    val bg: Color,
    val bgDark: Color,
    /** Raised panels (dialogs, result cards). */
    val panel: Color,
    val panelLine: Color,
    /** Translucent wash for grouped rows / tiles on the background. */
    val wash: Color,
    val text: Color,
    val textDim: Color,
    /** Highlight colour: primary buttons, active player, selected chips. */
    val accent: Color,
    val onAccent: Color,
    val cardFace: Color = Color(0xFFFFFCF4),
    val cardEdge: Color = Color(0x26000000),
    val red: Color = Color(0xFFC0392B),
    val ink: Color = Color(0xFF1C1C1C),
    val backA: Color = Color(0xFF7A1F2B),
    val backB: Color = Color(0xFF4E1019),
    val dark: Boolean = true,
) {
    val accentSoft: Color get() = accent.copy(alpha = 0.33f)

    companion object {
        /** Green card table (Euchre, Solitaire, Blackjack). */
        val Felt = GamePalette(
            bgLight = Color(0xFF2B7A52), bg = Color(0xFF1F5E3E), bgDark = Color(0xFF113726),
            panel = Color(0xFF0E2A1D), panelLine = Color(0x33FFFFFF), wash = Color(0x33000000),
            text = Color(0xFFF4F1E8), textDim = Color(0xB3F4F1E8),
            accent = Color(0xFFEBC66E), onAccent = Color(0xFF2A1F05),
        )

        /** Warm wood (Blocks). */
        val Wood = GamePalette(
            bgLight = Color(0xFFC98A55), bg = Color(0xFFA9683B), bgDark = Color(0xFF6E3F22),
            panel = Color(0xFF4A2A16), panelLine = Color(0x33FFE9CC), wash = Color(0x33301505),
            text = Color(0xFFFFF4E6), textDim = Color(0xCCFFF4E6),
            accent = Color(0xFFF2C46B), onAccent = Color(0xFF3A2208),
        )

        /** Light paper (Sudoku): dark ink on cream reads best for digits. */
        val Paper = GamePalette(
            bgLight = Color(0xFFFBF7EE), bg = Color(0xFFF4EEE0), bgDark = Color(0xFFE9E0CC),
            panel = Color(0xFFFFFDF8), panelLine = Color(0x22000000), wash = Color(0x0F000000),
            text = Color(0xFF1E2A3A), textDim = Color(0x991E2A3A),
            accent = Color(0xFF2F6FDB), onAccent = Color.White, dark = false,
        )
    }
}

val LocalPalette = staticCompositionLocalOf { GamePalette.Felt }

/** Shorthand inside composables: `P.accent`. */
val P: GamePalette @Composable get() = LocalPalette.current

val Serif = FontFamily.Serif

/** Drops font padding so a single glyph sits dead centre in a circle. [BEST_PRACTICES #8] */
val centeredGlyph = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    lineHeight = 1.em,
)

@Composable
fun DadTheme(palette: GamePalette, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        background = palette.bgDark, onBackground = palette.text,
        surface = palette.panel, onSurface = palette.text,
        onSurfaceVariant = palette.textDim, outline = palette.panelLine,
    ) else lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        background = palette.bg, onBackground = palette.text,
        surface = palette.panel, onSurface = palette.text,
        onSurfaceVariant = palette.textDim, outline = palette.panelLine,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
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
}

/** Radial table background centred slightly above middle. */
@Composable
fun Modifier.tableBackground(): Modifier {
    val p = P
    return background(
        Brush.radialGradient(
            0f to p.bgLight, 0.55f to p.bg, 1f to p.bgDark,
            center = Offset(540f, 900f), radius = 1500f,
        )
    )
}

/** Vertical gradient for menus and settings. */
@Composable
fun Modifier.menuBackground(): Modifier {
    val p = P
    return background(Brush.verticalGradient(listOf(p.bg, p.bgDark)))
}
