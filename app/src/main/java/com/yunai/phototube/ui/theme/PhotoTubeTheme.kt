package com.yunai.phototube.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import com.yunai.phototube.glass.*

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class ThemeAccent(val label: String, val light: Color, val dark: Color) {
    BLUE("雾蓝", Color(0xFF526D82), Color(0xFFA4B8C8)),
    PURPLE("烟紫", Color(0xFF736580), Color(0xFFBBB0C8)),
    ROSE("藕粉", Color(0xFF88616C), Color(0xFFCAAEB7)),
    ORANGE("暖砂", Color(0xFF806B54), Color(0xFFC8B8A4))
}

/** Semantic roles shared by every screen, sheet and custom glass component. */
@Immutable
data class AppColors(
    val background: Color, val groupedBackground: Color, val group: Color,
    val text: Color, val secondary: Color, val glass: Color, val separator: Color,
    val accent: Color, val selected: Color, val subtle: Color, val destructive: Color,
    val warning: Color,
)
val LocalAppColors = staticCompositionLocalOf<AppColors> { error("PhotoTubeTheme missing") }
val LocalThemeController = staticCompositionLocalOf<ThemeController> { error("PhotoTubeTheme missing") }
val LocalDarkTheme = staticCompositionLocalOf { false }
object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/** Media overlays deliberately retain contrast against images in either app theme. */
object MediaColors {
    const val readableScrimAlpha = .68f
    val destructive = Color(0xFFFF6961)
    val background = Color.Black
    val text = Color.White
    val secondary = Color(0xFFD1D1D6)
    val scrim = Color.Black
}

@Stable
class ThemeController(context: Context) {
    private val nightModeManager = context.applicationContext.getSystemService(UiModeManager::class.java)
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var mode by mutableStateOf(ThemeMode.entries.firstOrNull { it.name == preferences.getString("mode", null) } ?: ThemeMode.SYSTEM)
        private set
    var accent by mutableStateOf(ThemeAccent.entries.firstOrNull { it.name == preferences.getString("accent", null) } ?: ThemeAccent.BLUE)
        private set
    var glass by mutableStateOf(GlassAppearance(
        preferences.getFloat("blur", 2f).coerceIn(0f, 24f),
        preferences.getFloat("height", 12f).coerceIn(0f, 24f),
        preferences.getFloat("amount", 24f).coerceIn(0f, 48f),
        preferences.getBoolean("dispersion", false)))
        private set
    var opacity by mutableFloatStateOf(preferences.getFloat("opacity", .4f).coerceIn(.1f, .7f))
        private set
    fun updateGlass(value: GlassAppearance) {
        glass = value
        preferences.edit {
            putFloat("blur", value.blur); putFloat("height", value.refractionHeight)
            putFloat("amount", value.refractionAmount); putBoolean("dispersion", value.dispersion)
        }
    }
    fun updateOpacity(value: Float) { opacity = value; preferences.edit { putFloat("opacity", value) } }
    fun updateMode(value: ThemeMode) {
        mode = value
        preferences.edit { putString("mode", value.name) }
        syncNativeMode(value)
    }
    internal fun syncNativeMode(value: ThemeMode = mode) {
        // Unlike setNightMode, application AUTO clears the package override and
        // inherits system configuration. Also persists the native splash theme.
        nightModeManager.setApplicationNightMode(when(value) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        })
    }
    fun updateAccent(value: ThemeAccent) { accent = value; preferences.edit { putString("accent", value.name) } }
}

@Composable
fun PhotoTubeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val controller = remember { ThemeController(context) }
    LaunchedEffect(controller) { controller.syncNativeMode() }
    val dark = when(controller.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = remember(dark, controller.accent) {
        createThemePalette(dark, controller.accent)
    }
    val colors = palette.colors
    val mediaHandoff = remember { com.yunai.phototube.ui.media.MediaSourceHandoff() }
    CompositionLocalProvider(
        com.yunai.phototube.ui.media.LocalMediaSourceHandoff provides mediaHandoff,
        LocalAppColors provides colors, LocalThemeController provides controller, LocalDarkTheme provides dark,
        LocalGlassPalette provides palette.glass
    ) {
        MaterialTheme(colorScheme = palette.scheme) {
            ThemeSystemBars()
            content()
        }
    }
}

/** Call inside each fullscreen Dialog as it owns a separate Window. */
@Composable
fun ThemeSystemBars(media: Boolean = false) {
    val view = LocalView.current
    val dark = LocalDarkTheme.current || media
    val context = LocalContext.current
    DisposableEffect(view, dark, context) {
        fun applyAppearance() {
            val window = (view.parent as? DialogWindowProvider)?.window ?: context.activity()?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                it.isNavigationBarContrastEnforced = false
            }
        }
        // Dialog updates can copy the underlying window's appearance after composition.
        // Apply after those updates and again when this window regains focus.
        val update = Runnable { applyAppearance() }
        val observer = view.viewTreeObserver
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) view.post(update)
        }
        observer.addOnWindowFocusChangeListener(listener)
        view.post(update)
        onDispose {
            view.removeCallbacks(update)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
        }
    }
}
private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
