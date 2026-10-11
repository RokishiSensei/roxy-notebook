package com.lexi.wordbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lexi.wordbook.data.BackgroundConfig
import java.io.File

/**
 * Paints one custom background (solid colour or photo) behind [content].
 *
 * The main interface and 干净浏览 pass different [config]s, so the two can be customised
 * independently. [BackgroundConfig.transparency] controls the layer's own alpha — at 0 the
 * background is invisible and the Material surface colour shows through instead.
 */
@Composable
fun BackgroundLayer(
    config: BackgroundConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val imagePath = config.effectiveImagePath
    val imageFile = remember(imagePath) { imagePath.takeIf { it.isNotBlank() }?.let(::File) }
    val hasImage = imageFile?.exists() == true
    val solid = config.effectiveColor.takeIf { it != 0L }?.toArgbColor()
    val alpha = config.layerAlpha

    Box(modifier.fillMaxSize()) {
        // The Material surface colour is always painted first: when 背景浓度 is 0 this is what
        // the user sees, and it keeps text readable at every other setting too.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        )

        when {
            hasImage -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = alpha,
                    modifier = Modifier.fillMaxSize()
                )
                // A light scrim keeps text legible on busy photos without hiding them. It gets
                // weaker as the background gets stronger.
                val scrim = (0.30f - 0.26f * alpha).coerceIn(0.04f, 0.30f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = scrim),
                                    MaterialTheme.colorScheme.surface.copy(alpha = scrim + 0.05f)
                                )
                            )
                        )
                )
            }

            solid != null -> Box(
                Modifier
                    .fillMaxSize()
                    .background(solid.copy(alpha = alpha))
            )
        }

        content()
    }
}

/**
 * Makes the Material surface layers translucent while a background is active, so the photo or
 * colour actually shows through the app's own surfaces.
 *
 * [surfaceAlpha] comes from 背景浓度: the stronger the background, the more the surfaces step
 * aside. Without this step the screens would paint an opaque `surface` colour on top and the
 * background would stay invisible no matter how the slider is set.
 */
@Composable
fun transparentSurfaceScheme(
    scheme: androidx.compose.material3.ColorScheme,
    backgroundActive: Boolean,
    surfaceAlpha: Float = 0.62f
): androidx.compose.material3.ColorScheme {
    if (!backgroundActive) return scheme
    val glass = surfaceAlpha.coerceIn(0.2f, 1f)
    val glassStrong = (glass + 0.14f).coerceAtMost(1f)
    return scheme.copy(
        surface = scheme.surface.copy(alpha = glass),
        surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = glass),
        surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = glass),
        surfaceContainer = scheme.surfaceContainer.copy(alpha = glass),
        surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = glassStrong),
        surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = glassStrong),
        background = scheme.background.copy(alpha = glass)
    )
}

/** The stored background colour is a plain ARGB value (0xFFEDF3FF), so a cast is enough. */
fun Long.toArgbColor(): Color = Color(toInt())

/** Same conversion for callers that are not in a composable scope. */
fun argbColor(value: Long): Color = Color(value.toInt())
