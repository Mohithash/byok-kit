@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package __PKG__.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

/**
 * Deep shade for the far end of hero gradients: [Brand.heroDeep] normally, derived from the wallpaper
 * primary when Material You is on. Prefer `LocalHeroDeep.current` over `Brand.heroDeep` in new screens.
 */
val LocalHeroDeep = staticCompositionLocalOf { Color(0xFF102030) }

/** True when [AppTheme]'s `dynamic` flag can take effect (Android 12+). */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Same hue and saturation, value pulled down to a deep shade. */
private fun Color.deep(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[2] = 0.30f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The app theme. [darkTheme] follows the system unless the caller passes the user's light/dark choice;
 * [dynamic] swaps the generated brand palette for the wallpaper-based Material You scheme on Android 12+
 * (ignored on older versions, where the brand palette is used).
 */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val useDynamic = dynamic && dynamicColorAvailable
    val scheme = if (useDynamic) {
        val ctx = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (darkTheme) Brand.dark else Brand.light
    val deep = if (useDynamic) scheme.primary.deep() else Brand.heroDeep
    CompositionLocalProvider(LocalHeroDeep provides deep) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}
