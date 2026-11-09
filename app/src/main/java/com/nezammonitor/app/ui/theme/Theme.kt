package com.nezammonitor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Legacy constants — kept for compatibility. Nothing in the app reads them;
// the real theme now comes from [NezamMonitorTheme] below.
// ---------------------------------------------------------------------------
val Primary = Color(0xFF1565C0)
val PrimaryDark = Color(0xFF0D47A1)
val Secondary = Color(0xFF26A69A)
val Background = Color(0xFFF5F5F5)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1A1A1A)
val OnSurfaceVariant = Color(0xFF666666)
val Error = Color(0xFFD32F2F)
val Success = Color(0xFF388E3C)
val Warning = Color(0xFFF57C00)
val CardBorder = Color(0xFFE0E0E0)
val Divider = Color(0xFFE0E0E0)

// Typography sizes (legacy helpers, kept)
object AppTypography {
    val TitleLarge = 22.sp
    val TitleMedium = 18.sp
    val TitleSmall = 16.sp
    val BodyLarge = 15.sp
    val BodyMedium = 14.sp
    val BodySmall = 12.sp
    val LabelLarge = 14.sp
    val LabelMedium = 12.sp
    val LabelSmall = 10.sp
    val HeadlineLarge = 28.sp
}

// Font sizes for accessibility
object FontScale {
    const val Normal = 1.0f
    const val Large = 1.15f
    const val ExtraLarge = 1.3f
}

// Spacing
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

// Corner radius
object AppCorners {
    val small = 8
    val medium = 12
    val large = 16
}

// ===========================================================================
// 5-theme design system — UAC-style visual language:
//
// * Deep vertical gradient background (top → middle → bottom) instead of a
//   flat background colour. Dark variants are deep tinted navy, like the
//   reference app's #020913 → #04101C → #071421; light variants are airy
//   tints of the theme colour.
// * Glassy cards: surface colour + a hairline border (white ~12% on dark,
//   dark ~10% on light) and NO elevation shadow.
// * Vivid electric accents in dark variants (blue #299EFF, cyan #27D7FF,
//   green #25F58A, amber #FFB44A) so the primary action always pops.
// * Small muted secondary text + big semi-bold numbers (the reference
//   app's 22sp value / 11sp label rhythm).
//
// Every theme ships a light AND a dark ColorScheme. The light/dark/system
// mode (themeMode 0/1/2 in AppPreferences) picks which variant is used, the
// theme itself picks the identity. Status colours (success / warning / error /
// info) are deliberately the SAME in every theme so their meaning never
// changes with the theme.
// ===========================================================================

/** The five selectable visual identities. The stored value is [name]. */
enum class AppTheme(val persianName: String, val description: String) {
    MIDNIGHT(
        persianName = "نیمه‌شب",
        description = "تیره و سرمه‌ای؛ مناسب کار طولانی و حرفه‌ای"
    ),
    OCEAN(
        persianName = "اقیانوسی",
        description = "آبی و فیروزه‌ای؛ مدرن و آرام"
    ),
    EMERALD(
        persianName = "زمردی",
        description = "سبز زمردی و طلایی؛ لوکس و رسمی"
    ),
    GRAPHITE(
        persianName = "گرافیتی",
        description = "ذغالی و نقره‌ای؛ مینیمال و رسمی"
    ),
    LIGHT_PREMIUM(
        persianName = "روشن لوکس",
        description = "روشن، تمیز و ملایم؛ پیش‌فرض برنامه"
    );

    companion object {
        fun fromName(name: String?): AppTheme =
            values().firstOrNull { it.name == name } ?: LIGHT_PREMIUM
    }
}

/** One light + one dark variant of a theme, expressed as raw colours. */
private data class Variant(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val gradientTop: Color,
    val gradientMiddle: Color,
    val gradientBottom: Color,
    val cardBorder: Color
)

private data class ThemePalettes(val light: Variant, val dark: Variant)

private val palettes: Map<AppTheme, ThemePalettes> = mapOf(
    AppTheme.MIDNIGHT to ThemePalettes(
        light = Variant(
            primary = Color(0xFF1E40AF), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = Color(0xFF0E7490), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCFFAFE), onSecondaryContainer = Color(0xFF155E75),
            tertiary = Color(0xFF6D28D9), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF4C1D95),
            background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF94A3B8), outlineVariant = Color(0xFFCBD5E1),
            gradientTop = Color(0xFFF8FAFC), gradientMiddle = Color(0xFFEFF6FF),
            gradientBottom = Color(0xFFDBEAFE), cardBorder = Color(0x1A0F172A)
        ),
        dark = Variant(
            primary = Color(0xFF299EFF), onPrimary = Color(0xFF04101C),
            primaryContainer = Color(0xFF12395E), onPrimaryContainer = Color(0xFFBFDBFE),
            secondary = Color(0xFF27D7FF), onSecondary = Color(0xFF04101C),
            secondaryContainer = Color(0xFF0B3540), onSecondaryContainer = Color(0xFFA5F3FC),
            tertiary = Color(0xFFA78BFA), onTertiary = Color(0xFF2E1065),
            tertiaryContainer = Color(0xFF4C1D95), onTertiaryContainer = Color(0xFFEDE9FE),
            background = Color(0xFF020913), onBackground = Color(0xFFE8EEF4),
            surface = Color(0xFF101C29), onSurface = Color(0xFFE8EEF4),
            surfaceVariant = Color(0xFF16283A), onSurfaceVariant = Color(0xFF8D99A6),
            outline = Color(0xFF40536A), outlineVariant = Color(0xFF22364A),
            gradientTop = Color(0xFF020913), gradientMiddle = Color(0xFF04101C),
            gradientBottom = Color(0xFF071421), cardBorder = Color(0x20FFFFFF)
        )
    ),
    AppTheme.OCEAN to ThemePalettes(
        light = Variant(
            primary = Color(0xFF0E7490), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCFFAFE), onPrimaryContainer = Color(0xFF155E75),
            secondary = Color(0xFF0891B2), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0F2FE), onSecondaryContainer = Color(0xFF0C4A6E),
            tertiary = Color(0xFF0D9488), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFCCFBF1), onTertiaryContainer = Color(0xFF134E4A),
            background = Color(0xFFF0F9FF), onBackground = Color(0xFF082F49),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF082F49),
            surfaceVariant = Color(0xFFE0F2FE), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF7DD3FC), outlineVariant = Color(0xFFBAE6FD),
            gradientTop = Color(0xFFF0F9FF), gradientMiddle = Color(0xFFE0F2FE),
            gradientBottom = Color(0xFFBAE6FD), cardBorder = Color(0x1A082F49)
        ),
        dark = Variant(
            primary = Color(0xFF22D3EE), onPrimary = Color(0xFF083344),
            primaryContainer = Color(0xFF155E75), onPrimaryContainer = Color(0xFFCFFAFE),
            secondary = Color(0xFF38BDF8), onSecondary = Color(0xFF082F49),
            secondaryContainer = Color(0xFF0C4A6E), onSecondaryContainer = Color(0xFFE0F2FE),
            tertiary = Color(0xFF2DD4BF), onTertiary = Color(0xFF042F2E),
            tertiaryContainer = Color(0xFF134E4A), onTertiaryContainer = Color(0xFFCCFBF1),
            background = Color(0xFF03222B), onBackground = Color(0xFFE0F2FE),
            surface = Color(0xFF0B3540), onSurface = Color(0xFFE0F2FE),
            surfaceVariant = Color(0xFF134E5E), onSurfaceVariant = Color(0xFFA5D8E8),
            outline = Color(0xFF155E75), outlineVariant = Color(0xFF164E63),
            gradientTop = Color(0xFF03222B), gradientMiddle = Color(0xFF062A33),
            gradientBottom = Color(0xFF0A3540), cardBorder = Color(0x20FFFFFF)
        )
    ),
    AppTheme.EMERALD to ThemePalettes(
        light = Variant(
            primary = Color(0xFF047857), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF065F46),
            secondary = Color(0xFF065F46), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFA7F3D0), onSecondaryContainer = Color(0xFF064E3B),
            tertiary = Color(0xFFB45309), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF78350F),
            background = Color(0xFFECFDF5), onBackground = Color(0xFF022C22),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF022C22),
            surfaceVariant = Color(0xFFD1FAE5), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF6EE7B7), outlineVariant = Color(0xFFA7F3D0),
            gradientTop = Color(0xFFECFDF5), gradientMiddle = Color(0xFFD1FAE5),
            gradientBottom = Color(0xFFA7F3D0), cardBorder = Color(0x1A022C22)
        ),
        dark = Variant(
            primary = Color(0xFF25F58A), onPrimary = Color(0xFF022C22),
            primaryContainer = Color(0xFF064E3B), onPrimaryContainer = Color(0xFFD1FAE5),
            secondary = Color(0xFF6EE7B7), onSecondary = Color(0xFF022C22),
            secondaryContainer = Color(0xFF065F46), onSecondaryContainer = Color(0xFFA7F3D0),
            tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF451A03),
            tertiaryContainer = Color(0xFF78350F), onTertiaryContainer = Color(0xFFFEF3C7),
            background = Color(0xFF02120C), onBackground = Color(0xFFD1FAE5),
            surface = Color(0xFF0A3325), onSurface = Color(0xFFD1FAE5),
            surfaceVariant = Color(0xFF14532D), onSurfaceVariant = Color(0xFFA7F3D0),
            outline = Color(0xFF065F46), outlineVariant = Color(0xFF064E3B),
            gradientTop = Color(0xFF02120C), gradientMiddle = Color(0xFF041F16),
            gradientBottom = Color(0xFF062B1F), cardBorder = Color(0x20FFFFFF)
        )
    ),
    AppTheme.GRAPHITE to ThemePalettes(
        light = Variant(
            primary = Color(0xFF374151), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111827),
            secondary = Color(0xFF6B7280), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF3F4F6), onSecondaryContainer = Color(0xFF1F2937),
            tertiary = Color(0xFF64748B), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE2E8F0), onTertiaryContainer = Color(0xFF1E293B),
            background = Color(0xFFF9FAFB), onBackground = Color(0xFF111827),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF4B5563),
            outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFD1D5DB),
            gradientTop = Color(0xFFF9FAFB), gradientMiddle = Color(0xFFF3F4F6),
            gradientBottom = Color(0xFFE5E7EB), cardBorder = Color(0x1A111827)
        ),
        dark = Variant(
            primary = Color(0xFFF9FAFB), onPrimary = Color(0xFF111827),
            primaryContainer = Color(0xFF374151), onPrimaryContainer = Color(0xFFF9FAFB),
            secondary = Color(0xFF9CA3AF), onSecondary = Color(0xFF111827),
            secondaryContainer = Color(0xFF4B5563), onSecondaryContainer = Color(0xFFE5E7EB),
            tertiary = Color(0xFFCBD5E1), onTertiary = Color(0xFF1E293B),
            tertiaryContainer = Color(0xFF475569), onTertiaryContainer = Color(0xFFF1F5F9),
            background = Color(0xFF0B0D12), onBackground = Color(0xFFF9FAFB),
            surface = Color(0xFF1F2937), onSurface = Color(0xFFF9FAFB),
            surfaceVariant = Color(0xFF374151), onSurfaceVariant = Color(0xFFD1D5DB),
            outline = Color(0xFF6B7280), outlineVariant = Color(0xFF4B5563),
            gradientTop = Color(0xFF0B0D12), gradientMiddle = Color(0xFF111827),
            gradientBottom = Color(0xFF1F2937), cardBorder = Color(0x20FFFFFF)
        )
    ),
    AppTheme.LIGHT_PREMIUM to ThemePalettes(
        light = Variant(
            primary = Color(0xFF2563EB), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E40AF),
            secondary = Color(0xFF4F46E5), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF312E81),
            tertiary = Color(0xFF0D9488), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFCCFBF1), onTertiaryContainer = Color(0xFF134E4A),
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF94A3B8), outlineVariant = Color(0xFFE2E8F0),
            gradientTop = Color(0xFFFFFFFF), gradientMiddle = Color(0xFFF8FAFC),
            gradientBottom = Color(0xFFEFF6FF), cardBorder = Color(0x1A0F172A)
        ),
        dark = Variant(
            primary = Color(0xFF60A5FA), onPrimary = Color(0xFF172554),
            primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = Color(0xFFA5B4FC), onSecondary = Color(0xFF1E1B4B),
            secondaryContainer = Color(0xFF3730A3), onSecondaryContainer = Color(0xFFE0E7FF),
            tertiary = Color(0xFF2DD4BF), onTertiary = Color(0xFF042F2E),
            tertiaryContainer = Color(0xFF134E4A), onTertiaryContainer = Color(0xFFCCFBF1),
            background = Color(0xFF0A1120), onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF1E293B), onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF334155), onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF475569), outlineVariant = Color(0xFF334155),
            gradientTop = Color(0xFF0A1120), gradientMiddle = Color(0xFF0F172A),
            gradientBottom = Color(0xFF1E293B), cardBorder = Color(0x20FFFFFF)
        )
    )
)

// Status colours are theme-independent (Material 3 defaults for error).
private val lightError = Color(0xFFB3261E)
private val lightOnError = Color(0xFFFFFFFF)
private val lightErrorContainer = Color(0xFFF9DEDC)
private val lightOnErrorContainer = Color(0xFF410E0B)
private val darkError = Color(0xFFF2B8B5)
private val darkOnError = Color(0xFF601410)
private val darkErrorContainer = Color(0xFF8C1D18)
private val darkOnErrorContainer = Color(0xFFF9DEDC)

/** Builds the Material 3 ColorScheme for a theme + light/dark variant. */
fun appColorScheme(theme: AppTheme, darkTheme: Boolean): ColorScheme {
    val p = palettes[theme] ?: palettes.getValue(AppTheme.LIGHT_PREMIUM)
    val v = if (darkTheme) p.dark else p.light
    return if (darkTheme) {
        darkColorScheme(
            primary = v.primary, onPrimary = v.onPrimary,
            primaryContainer = v.primaryContainer, onPrimaryContainer = v.onPrimaryContainer,
            secondary = v.secondary, onSecondary = v.onSecondary,
            secondaryContainer = v.secondaryContainer, onSecondaryContainer = v.onSecondaryContainer,
            tertiary = v.tertiary, onTertiary = v.onTertiary,
            tertiaryContainer = v.tertiaryContainer, onTertiaryContainer = v.onTertiaryContainer,
            background = v.background, onBackground = v.onBackground,
            surface = v.surface, onSurface = v.onSurface,
            surfaceVariant = v.surfaceVariant, onSurfaceVariant = v.onSurfaceVariant,
            surfaceTint = v.primary,
            error = darkError, onError = darkOnError,
            errorContainer = darkErrorContainer, onErrorContainer = darkOnErrorContainer,
            outline = v.outline, outlineVariant = v.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = v.primary, onPrimary = v.onPrimary,
            primaryContainer = v.primaryContainer, onPrimaryContainer = v.onPrimaryContainer,
            secondary = v.secondary, onSecondary = v.onSecondary,
            secondaryContainer = v.secondaryContainer, onSecondaryContainer = v.onSecondaryContainer,
            tertiary = v.tertiary, onTertiary = v.onTertiary,
            tertiaryContainer = v.tertiaryContainer, onTertiaryContainer = v.onTertiaryContainer,
            background = v.background, onBackground = v.onBackground,
            surface = v.surface, onSurface = v.onSurface,
            surfaceVariant = v.surfaceVariant, onSurfaceVariant = v.onSurfaceVariant,
            surfaceTint = v.primary,
            error = lightError, onError = lightOnError,
            errorContainer = lightErrorContainer, onErrorContainer = lightOnErrorContainer,
            outline = v.outline, outlineVariant = v.outlineVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Semantic (status) colours — same meaning in every theme.
// Dark variants use the reference app's vivid state colours:
// green #25F58A, amber #FFB44A, cyan #27D7FF.
// ---------------------------------------------------------------------------
data class NezamSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val lightSemantic = NezamSemanticColors(
    success = Color(0xFF15803D), onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFCE7), onSuccessContainer = Color(0xFF14532D),
    warning = Color(0xFFB45309), onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7), onWarningContainer = Color(0xFF78350F),
    info = Color(0xFF0369A1), onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFE0F2FE), onInfoContainer = Color(0xFF0C4A6E)
)

private val darkSemantic = NezamSemanticColors(
    success = Color(0xFF25F58A), onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF14532D), onSuccessContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFFFB44A), onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF78350F), onWarningContainer = Color(0xFFFEF3C7),
    info = Color(0xFF27D7FF), onInfo = Color(0xFF082F49),
    infoContainer = Color(0xFF0C4A6E), onInfoContainer = Color(0xFFE0F2FE)
)

val LocalNezamSemanticColors = staticCompositionLocalOf { lightSemantic }

/** Read the current semantic (status) colours inside a composable. */
val MaterialTheme.nezamSemantic: NezamSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNezamSemanticColors.current

// ---------------------------------------------------------------------------
// Extra visual language: gradient background stops + hairline card border,
// one set per theme variant. Read via MaterialTheme.nezamExtras.
// ---------------------------------------------------------------------------
data class NezamGradientColors(val top: Color, val middle: Color, val bottom: Color)

data class NezamExtraColors(val gradient: NezamGradientColors, val cardBorder: Color)

private fun extrasFor(theme: AppTheme, darkTheme: Boolean): NezamExtraColors {
    val p = palettes[theme] ?: palettes.getValue(AppTheme.LIGHT_PREMIUM)
    val v = if (darkTheme) p.dark else p.light
    return NezamExtraColors(
        gradient = NezamGradientColors(v.gradientTop, v.gradientMiddle, v.gradientBottom),
        cardBorder = v.cardBorder
    )
}

val LocalNezamExtras = staticCompositionLocalOf {
    NezamExtraColors(
        gradient = NezamGradientColors(
            Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFEFF6FF)
        ),
        cardBorder = Color(0x1A0F172A)
    )
}

/** Read the current gradient + hairline border colours inside a composable. */
val MaterialTheme.nezamExtras: NezamExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNezamExtras.current

/** Darken a colour by scaling its RGB channels (for gradient buttons). */
fun Color.darker(factor: Float = 0.75f): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

// ---------------------------------------------------------------------------
// Shapes & typography — one soft, rounded language for the whole app.
// Every Card / Button / TextField that uses the MaterialTheme defaults picks
// these up automatically, so all screens change together.
// ---------------------------------------------------------------------------
val NezamShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

fun nezamTypography(fontScale: Float = 1.0f): Typography {
    fun scaled(sp: Int) = (sp * fontScale).sp
    val family = FontFamily.Default
    return Typography(
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = scaled(28)),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = scaled(22)),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = scaled(22)),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = scaled(18)),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = scaled(16)),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = scaled(15)),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = scaled(14)),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = scaled(12)),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = scaled(14)),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = scaled(12)),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = scaled(11))
    )
}

/**
 * The single theme entry point. Wraps MaterialTheme with the selected
 * [AppTheme] + light/dark variant and provides the semantic colours and
 * the gradient / border extras. Changing the theme recomposes the whole
 * tree instantly — no restart, no data reload (the ViewModel flows are
 * untouched).
 */
@Composable
fun NezamMonitorTheme(
    appTheme: AppTheme = AppTheme.LIGHT_PREMIUM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalNezamSemanticColors provides if (darkTheme) darkSemantic else lightSemantic,
        LocalNezamExtras provides extrasFor(appTheme, darkTheme)
    ) {
        MaterialTheme(
            colorScheme = appColorScheme(appTheme, darkTheme),
            shapes = NezamShapes,
            typography = nezamTypography(fontScale),
            content = content
        )
    }
}
