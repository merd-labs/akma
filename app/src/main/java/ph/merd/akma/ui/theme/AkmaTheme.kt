package ph.merd.akma.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ph.merd.akma.R

@Immutable
data class AkmaColors(
    val textPrimary: Color = Color(AkmaTokens.TEXT_PRIMARY),
    val textSecondary: Color = Color(AkmaTokens.TEXT_SECONDARY),
    val textBrandStrong: Color = Color(AkmaTokens.TEXT_BRAND_STRONG),
    val textOnBrand: Color = Color(AkmaTokens.TEXT_ON_BRAND),
    val bgApp: Color = Color(AkmaTokens.BG_APP),
    val bgSurface: Color = Color(AkmaTokens.BG_SURFACE),
    val bgSubtle: Color = Color(AkmaTokens.BG_SUBTLE),
    val bgMuted: Color = Color(AkmaTokens.BG_MUTED),
    val bgBrand: Color = Color(AkmaTokens.BG_BRAND),
    val bgBrandStrong: Color = Color(AkmaTokens.BG_BRAND_STRONG),
    val bgBrandSubtle: Color = Color(AkmaTokens.BG_BRAND_SUBTLE),
    val bgBrandMuted: Color = Color(AkmaTokens.BG_BRAND_MUTED),
    val bgSuccess: Color = Color(AkmaTokens.BG_SUCCESS),
    val bgScrim: Color = Color(AkmaTokens.BG_SCRIM),
    val borderDefault: Color = Color(AkmaTokens.BORDER_DEFAULT),
    val borderStrong: Color = Color(AkmaTokens.BORDER_STRONG),
)

val AkmaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun akmaStyle(weight: FontWeight, size: Int, lineHeight: Int, trackingPercent: Double = 0.0) = TextStyle(
    fontFamily = AkmaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = (trackingPercent / 100).em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Figma text styles. Letter spacing is a percentage of font size, as in Figma. */
@Immutable
data class AkmaTypography(
    val display: TextStyle = akmaStyle(FontWeight.ExtraBold, 32, 40, -2.0),
    val titleM: TextStyle = akmaStyle(FontWeight.Bold, 20, 28, -1.0),
    val titleS: TextStyle = akmaStyle(FontWeight.Bold, 17, 24, -0.5),
    val wordmark: TextStyle = akmaStyle(FontWeight.ExtraBold, 17, 24, -0.5),
    val wordmarkL: TextStyle = akmaStyle(FontWeight.ExtraBold, 20, 28, -2.0),
    val bodyL: TextStyle = akmaStyle(FontWeight.Normal, 16, 24),
    val bodyLStrong: TextStyle = akmaStyle(FontWeight.SemiBold, 16, 24),
    val bodyM: TextStyle = akmaStyle(FontWeight.Normal, 14, 20),
    val bodyMStrong: TextStyle = akmaStyle(FontWeight.SemiBold, 14, 20),
    val labelL: TextStyle = akmaStyle(FontWeight.Bold, 16, 20),
    val labelM: TextStyle = akmaStyle(FontWeight.SemiBold, 14, 20),
    val labelMStrong: TextStyle = akmaStyle(FontWeight.Bold, 14, 20),
    val labelS: TextStyle = akmaStyle(FontWeight.Bold, 12, 16, 1.0),
    val caption: TextStyle = akmaStyle(FontWeight.Medium, 12, 16),
)

object AkmaSpacing {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
}

object AkmaRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val full = RoundedCornerShape(percent = 50)
}

private val LocalAkmaColors = staticCompositionLocalOf { AkmaColors() }
private val LocalAkmaTypography = staticCompositionLocalOf { AkmaTypography() }

object AkmaTheme {
    val colors: AkmaColors
        @Composable @ReadOnlyComposable get() = LocalAkmaColors.current
    val type: AkmaTypography
        @Composable @ReadOnlyComposable get() = LocalAkmaTypography.current
}

/** Light theme only: the Figma file defines no dark palette. */
@Composable
fun AkmaTheme(content: @Composable () -> Unit) {
    val colors = AkmaColors()
    val type = AkmaTypography()
    val scheme = lightColorScheme(
        primary = colors.bgBrandStrong,
        onPrimary = colors.textOnBrand,
        primaryContainer = colors.bgBrandMuted,
        onPrimaryContainer = colors.textBrandStrong,
        background = colors.bgApp,
        onBackground = colors.textPrimary,
        surface = colors.bgSurface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.bgSubtle,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.borderStrong,
        outlineVariant = colors.borderDefault,
        scrim = colors.bgScrim,
    )
    CompositionLocalProvider(
        LocalAkmaColors provides colors,
        LocalAkmaTypography provides type,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.copy(bodyLarge = type.bodyL), content = content)
    }
}
