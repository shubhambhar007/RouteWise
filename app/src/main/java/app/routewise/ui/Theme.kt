package app.routewise.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.routewise.R

val Ink = Color(0xFF172D27)
val Teal = Color(0xFF17624F)
val Muted = Color(0xFF66756D)
val Coral = Color(0xFFBD5A49)

@OptIn(ExperimentalTextApi::class)
@Composable
fun RouteWiseTheme(content: @Composable () -> Unit) {
    val font = FontFamily(
        Font(R.font.manrope, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
    fun type(size: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = (size + 7).sp, fontWeight = weight, letterSpacing = 0.sp)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal, onPrimary = Color.White,
            primaryContainer = Color(0xFFDDEDE3), onPrimaryContainer = Ink,
            secondary = Coral, secondaryContainer = Color(0xFFF8E7DF),
            tertiary = Color(0xFFD18A2E), onTertiary = Color.White,
            background = Color(0xFFF3F6F1), surface = Color.White,
            onSurface = Ink, onBackground = Ink, onSurfaceVariant = Muted,
            surfaceVariant = Color(0xFFE8EEE8), outline = Color(0xFFBCC9C0),
        ),
        typography = Typography(
            headlineLarge = type(30, FontWeight.SemiBold), headlineMedium = type(26, FontWeight.SemiBold),
            headlineSmall = type(22, FontWeight.SemiBold), titleLarge = type(20, FontWeight.SemiBold),
            titleMedium = type(16, FontWeight.SemiBold), titleSmall = type(14, FontWeight.SemiBold),
            bodyLarge = type(16), bodyMedium = type(14), bodySmall = type(12),
            labelLarge = type(14, FontWeight.SemiBold), labelMedium = type(12, FontWeight.SemiBold), labelSmall = type(11),
        ), content = content,
    )
}
