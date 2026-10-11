package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.data.BackgroundConfig
import com.lexi.wordbook.data.BackgroundSource
import com.lexi.wordbook.data.CleanSortMode
import com.lexi.wordbook.data.PreferencesRepository
import com.lexi.wordbook.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main interface and 干净浏览 keep independent backgrounds; these tests lock in that
 * separation (which is what makes 「首页背景」 and 「干净浏览背景」 configurable one by one).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackgroundPrefsTest {

    private fun repository(): PreferencesRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return PreferencesRepository(context)
    }

    @Test
    fun `defaults have no background but are enabled`() {
        val prefs = repository().state.value
        assertFalse(prefs.mainBackground.isSet)
        assertFalse(prefs.cleanBackground.isSet)
        assertTrue(prefs.backgroundEnabled)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
    }

    @Test
    fun `setting a main colour leaves the clean background untouched`() {
        val repo = repository()
        repo.update { it.copy(mainBackground = it.mainBackground.copy(color = 0xFFEDF3FF)) }

        val prefs = repo.state.value
        assertTrue(prefs.mainBackground.isSet)
        assertEquals(0xFFEDF3FF, prefs.mainBackground.effectiveColor)
        assertFalse(prefs.cleanBackground.isSet)
    }

    @Test
    fun `both backgrounds can differ at the same time`() {
        val repo = repository()
        repo.update {
            it.copy(
                mainBackground = BackgroundConfig(
                    source = BackgroundSource.SOLID,
                    color = 0xFF2B2B31
                ),
                cleanBackground = BackgroundConfig(
                    source = BackgroundSource.IMAGE,
                    imagePath = "/data/user/0/com.lexi.wordbook/files/background_clean/background.jpg",
                    opacity = 60
                )
            )
        }

        val prefs = repo.state.value
        assertEquals(BackgroundSource.SOLID, prefs.mainBackground.source)
        assertEquals(0xFF2B2B31, prefs.mainBackground.effectiveColor)
        assertEquals("", prefs.mainBackground.effectiveImagePath)

        assertEquals(BackgroundSource.IMAGE, prefs.cleanBackground.source)
        assertTrue(prefs.cleanBackground.effectiveImagePath.endsWith("background.jpg"))
        assertEquals(0L, prefs.cleanBackground.effectiveColor)
        assertEquals(60, prefs.cleanBackground.opacity)
    }

    @Test
    fun `values survive a reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val first = PreferencesRepository(context)
        first.update {
            it.copy(
                mainBackground = BackgroundConfig(color = 0xFFEAF6EE, opacity = 85),
                cleanBackground = BackgroundConfig(color = 0xFF14202B, opacity = 30),
                backgroundEnabled = false
            )
        }

        // A second instance reads the same SharedPreferences.
        val reloaded = PreferencesRepository(context).state.value
        assertEquals(0xFFEAF6EE, reloaded.mainBackground.effectiveColor)
        assertEquals(85, reloaded.mainBackground.opacity)
        assertEquals(0xFF14202B, reloaded.cleanBackground.effectiveColor)
        assertEquals(30, reloaded.cleanBackground.opacity)
        assertFalse(reloaded.backgroundEnabled)
    }

    @Test
    fun `switching source keeps only the matching value`() {
        val config = BackgroundConfig(color = 0xFFEDF3FF)
        assertTrue(config.isSet)
        assertEquals(0xFFEDF3FF, config.effectiveColor)
        assertEquals("", config.effectiveImagePath)

        // Asking for an image ignores the leftover colour.
        val asImage = config.copy(source = BackgroundSource.IMAGE, imagePath = "/tmp/a.jpg")
        assertEquals(0L, asImage.effectiveColor)
        assertEquals("/tmp/a.jpg", asImage.effectiveImagePath)

        // Selecting "default" (no colour, no path) is not "set".
        assertFalse(BackgroundConfig().isSet)
        assertFalse(BackgroundConfig(source = BackgroundSource.IMAGE, imagePath = "").isSet)
        assertFalse(BackgroundConfig(source = BackgroundSource.SOLID, color = 0L).isSet)
    }

    @Test
    fun `higher concentration shows more background and hides more surface`() {
        val off = BackgroundConfig(color = 0xFFEDF3FF, opacity = 0)
        assertEquals(0f, off.layerAlpha, 0.001f)
        // No background -> the surface stays fully opaque, so the plain theme is shown.
        assertEquals(1f, off.surfaceAlpha, 0.001f)

        val max = BackgroundConfig(color = 0xFFEDF3FF, opacity = 100)
        assertEquals(1f, max.layerAlpha, 0.001f)
        // Maximum background -> the surfaces step aside the most (72% of the way).
        assertEquals(0.28f, max.surfaceAlpha, 0.001f)

        val middle = BackgroundConfig(color = 0xFFEDF3FF, opacity = 50)
        assertEquals(0.5f, middle.layerAlpha, 0.001f)
        assertEquals(0.64f, middle.surfaceAlpha, 0.001f)
    }

    @Test
    fun `concentration is monotonic and clamped to 0-100`() {
        val alphas = (0..100 step 10).map { BackgroundConfig(opacity = it).layerAlpha }
        assertEquals(alphas.sorted(), alphas)
        val surface = (0..100 step 10).map { BackgroundConfig(opacity = it).surfaceAlpha }
        // A stronger background always means a more transparent surface.
        assertEquals(surface.sortedDescending(), surface)

        assertEquals(1f, BackgroundConfig(opacity = 999).layerAlpha, 0.001f)
        assertEquals(0f, BackgroundConfig(opacity = -50).layerAlpha, 0.001f)
    }

    @Test
    fun `concentration defaults to a visible value`() {
        assertEquals(70, BackgroundConfig().opacity)
        assertEquals(0.7f, BackgroundConfig().layerAlpha, 0.001f)
        assertTrue(BackgroundConfig.OPACITY_PRESETS.contains(100))
    }

    @Test
    fun `clean browse remembers sort mode and text scale`() {
        val repo = repository()
        assertEquals(CleanSortMode.ALPHABET, repo.state.value.cleanSortMode)
        assertEquals(1f, repo.state.value.cleanTextScale, 0.001f)

        repo.update {
            it.copy(
                cleanSortMode = CleanSortMode.RECENT,
                cleanTextScale = 1.35f
            )
        }

        val reloaded = PreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>()
        ).state.value
        assertEquals(CleanSortMode.RECENT, reloaded.cleanSortMode)
        assertEquals(1.35f, reloaded.cleanTextScale, 0.001f)
    }

    @Test
    fun `last read position is stored per group and survives a reload`() {
        val repo = repository()
        // Nothing recorded yet.
        assertEquals(null, repo.state.value.cleanLastRead[""])

        repo.update { it.copy(cleanLastRead = mapOf("" to "word-a", "g1" to "word-b")) }

        val reloaded = PreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>()
        ).state.value
        assertEquals("word-a", reloaded.cleanLastRead[""])
        assertEquals("word-b", reloaded.cleanLastRead["g1"])
        assertEquals(2, reloaded.cleanLastRead.size)
    }

    @Test
    fun `last read position round-trips awkward keys`() {
        val repo = repository()
        // Group ids and word ids never contain separators, but the encoder must not corrupt
        // anything if they ever do.
        val tricky = mapOf("a=b,c" to "id=1,2", "" to "plain")
        repo.update { it.copy(cleanLastRead = tricky) }

        val reloaded = PreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>()
        ).state.value
        assertEquals(tricky, reloaded.cleanLastRead)
    }

    @Test
    fun `the ungrouped bucket is a valid last read entry`() {
        val repo = repository()
        // "" is the key for 未分组; it must survive the encode/decode round trip.
        repo.update { it.copy(cleanLastRead = mapOf("" to "word-ungrouped")) }

        val reloaded = PreferencesRepository(
            ApplicationProvider.getApplicationContext<Context>()
        ).state.value
        assertEquals("word-ungrouped", reloaded.cleanLastRead[""])
    }

    @Test
    fun `an empty last read map is not persisted as garbage`() {
        val repo = repository()
        repo.update { it.copy(cleanLastRead = emptyMap()) }
        assertEquals(emptyMap<String, String>(), repo.state.value.cleanLastRead)
    }

    @Test
    fun `reset clears backgrounds too`() {
        val repo = repository()
        repo.update {
            it.copy(
                mainBackground = it.mainBackground.copy(color = 0xFFEDF3FF),
                backgroundEnabled = false
            )
        }
        repo.reset()
        assertFalse(repo.state.value.mainBackground.isSet)
        assertTrue(repo.state.value.backgroundEnabled)
    }
}
