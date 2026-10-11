package com.lexi.wordbook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lexi.wordbook.data.ThemeMode

/**
 * Brand palette: a modern "ink + electric indigo" scheme that stays legible for dense
 * list screens. Android 12+ users get Material You dynamic color when enabled.
 */
object BrandColors {
    val Primary = Color(0xFF4C5BD4)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFDFE0FF)
    val OnPrimaryContainer = Color(0xFF00105C)

    val Secondary = Color(0xFF00897B)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFB2F1E7)
    val OnSecondaryContainer = Color(0xFF00201B)

    val Tertiary = Color(0xFFE0518A)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFFFD9E3)
    val OnTertiaryContainer = Color(0xFF3E0021)

    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)

    val Surface = Color(0xFFFBF8FF)
    val OnSurface = Color(0xFF1B1B21)
    val SurfaceVariant = Color(0xFFE3E1EC)
    val OnSurfaceVariant = Color(0xFF46464F)
    val SurfaceContainer = Color(0xFFF1EEF7)
    val SurfaceContainerHigh = Color(0xFFEBE8F1)
    val SurfaceContainerLow = Color(0xFFF6F3FC)
    val Outline = Color(0xFF777680)
    val OutlineVariant = Color(0xFFC7C5D0)

    // Dark
    val DarkPrimary = Color(0xFFBEC2FF)
    val DarkOnPrimary = Color(0xFF1B2678)
    val DarkPrimaryContainer = Color(0xFF333D90)
    val DarkOnPrimaryContainer = Color(0xFFDFE0FF)

    val DarkSecondary = Color(0xFF96D5CB)
    val DarkOnSecondary = Color(0xFF003731)
    val DarkSecondaryContainer = Color(0xFF005048)
    val DarkOnSecondaryContainer = Color(0xFFB2F1E7)

    val DarkTertiary = Color(0xFFFFB1C8)
    val DarkOnTertiary = Color(0xFF5F1136)
    val DarkTertiaryContainer = Color(0xFF7D294C)
    val DarkOnTertiaryContainer = Color(0xFFFFD9E3)

    val DarkError = Color(0xFFFFB4AB)
    val DarkErrorContainer = Color(0xFF93000A)

    val DarkSurface = Color(0xFF131318)
    val DarkOnSurface = Color(0xFFE4E1E9)
    val DarkSurfaceVariant = Color(0xFF46464F)
    val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
    val DarkSurfaceContainer = Color(0xFF1F1F25)
    val DarkSurfaceContainerHigh = Color(0xFF2A2A30)
    val DarkSurfaceContainerLow = Color(0xFF1B1B21)
    val DarkOutline = Color(0xFF918F9A)
    val DarkOutlineVariant = Color(0xFF46464F)
}

/** Mastery accent colors, shared by the list, review cards and statistics. */
object StatusColors {
    val New = Color(0xFF7C8DB5)
    val Learning = Color(0xFFE0A32E)
    val Familiar = Color(0xFF2E9E7E)
    val Mastered = Color(0xFF4C5BD4)

    @Composable
    fun of(status: com.lexi.wordbook.data.LearningStatus): Color = when (status) {
        com.lexi.wordbook.data.LearningStatus.NEW -> New
        com.lexi.wordbook.data.LearningStatus.LEARNING -> Learning
        com.lexi.wordbook.data.LearningStatus.FAMILIAR -> Familiar
        com.lexi.wordbook.data.LearningStatus.MASTERED -> Mastered
    }
}

private val LightScheme = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = BrandColors.OnPrimary,
    primaryContainer = BrandColors.PrimaryContainer,
    onPrimaryContainer = BrandColors.OnPrimaryContainer,
    secondary = BrandColors.Secondary,
    onSecondary = BrandColors.OnSecondary,
    secondaryContainer = BrandColors.SecondaryContainer,
    onSecondaryContainer = BrandColors.OnSecondaryContainer,
    tertiary = BrandColors.Tertiary,
    onTertiary = BrandColors.OnTertiary,
    tertiaryContainer = BrandColors.TertiaryContainer,
    onTertiaryContainer = BrandColors.OnTertiaryContainer,
    error = BrandColors.Error,
    errorContainer = BrandColors.ErrorContainer,
    background = BrandColors.Surface,
    onBackground = BrandColors.OnSurface,
    surface = BrandColors.Surface,
    onSurface = BrandColors.OnSurface,
    surfaceVariant = BrandColors.SurfaceVariant,
    onSurfaceVariant = BrandColors.OnSurfaceVariant,
    surfaceContainer = BrandColors.SurfaceContainer,
    surfaceContainerHigh = BrandColors.SurfaceContainerHigh,
    surfaceContainerLow = BrandColors.SurfaceContainerLow,
    outline = BrandColors.Outline,
    outlineVariant = BrandColors.OutlineVariant
)

private val DarkScheme = darkColorScheme(
    primary = BrandColors.DarkPrimary,
    onPrimary = BrandColors.DarkOnPrimary,
    primaryContainer = BrandColors.DarkPrimaryContainer,
    onPrimaryContainer = BrandColors.DarkOnPrimaryContainer,
    secondary = BrandColors.DarkSecondary,
    onSecondary = BrandColors.DarkOnSecondary,
    secondaryContainer = BrandColors.DarkSecondaryContainer,
    onSecondaryContainer = BrandColors.DarkOnSecondaryContainer,
    tertiary = BrandColors.DarkTertiary,
    onTertiary = BrandColors.DarkOnTertiary,
    tertiaryContainer = BrandColors.DarkTertiaryContainer,
    onTertiaryContainer = BrandColors.DarkOnTertiaryContainer,
    error = BrandColors.DarkError,
    errorContainer = BrandColors.DarkErrorContainer,
    background = BrandColors.DarkSurface,
    onBackground = BrandColors.DarkOnSurface,
    surface = BrandColors.DarkSurface,
    onSurface = BrandColors.DarkOnSurface,
    surfaceVariant = BrandColors.DarkSurfaceVariant,
    onSurfaceVariant = BrandColors.DarkOnSurfaceVariant,
    surfaceContainer = BrandColors.DarkSurfaceContainer,
    surfaceContainerHigh = BrandColors.DarkSurfaceContainerHigh,
    surfaceContainerLow = BrandColors.DarkSurfaceContainerLow,
    outline = BrandColors.DarkOutline,
    outlineVariant = BrandColors.DarkOutlineVariant
)

@Composable
fun WordBookTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    /** When true the surface layers turn translucent so a custom background shows through. */
    backgroundActive: Boolean = false,
    /** Surface opacity driven by the background's transparency setting. */
    backgroundSurfaceAlpha: Float = 0.62f,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = com.lexi.wordbook.ui.components.transparentSurfaceScheme(
            scheme = baseScheme,
            backgroundActive = backgroundActive,
            surfaceAlpha = backgroundSurfaceAlpha
        ),
        typography = WordBookTypography,
        shapes = WordBookShapes,
        content = content
    )
}
