package com.lexi.wordbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lexi.wordbook.ui.MainScreen
import com.lexi.wordbook.ui.theme.WordBookTheme

/**
 * Single-activity host. Edge-to-edge is enabled explicitly so the app draws behind the
 * system bars on Android 15+ (where it is enforced) and on older releases alike.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as WordBookApp).container
        // A pending AI bubble from a previous run can never be answered.
        container.chat.dropPending()

        setContent {
            val preferences by container.preferences.state.collectAsStateWithLifecycle()
            // While 干净浏览 is open it owns the whole screen (and paints its own background),
            // so the main background must not shine through underneath it.
            var cleanBrowseOpen by remember { mutableStateOf(false) }
            val mainBackgroundActive = !cleanBrowseOpen &&
                preferences.backgroundEnabled &&
                preferences.mainBackground.isSet
            WordBookTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.useDynamicColor,
                backgroundActive = mainBackgroundActive,
                backgroundSurfaceAlpha = preferences.mainBackground.surfaceAlpha
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    // The main background is painted behind every screen; the theme makes the
                    // Material surfaces translucent so it stays visible.
                    com.lexi.wordbook.ui.components.BackgroundLayer(
                        config = if (mainBackgroundActive) {
                            preferences.mainBackground
                        } else {
                            com.lexi.wordbook.data.BackgroundConfig()
                        }
                    ) {
                        MainScreen(
                            onCleanBrowseVisibilityChange = { open -> cleanBrowseOpen = open }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as WordBookApp).container.speech.stop()
    }
}
