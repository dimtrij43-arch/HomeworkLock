package com.hwlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private val Light = lightColorScheme(
    primary = Color(0xFF3949AB), onPrimary = Color.White,
    secondary = Color(0xFF00897B), secondaryContainer = Color(0xFFDDF3EF), onSecondaryContainer = Color(0xFF00403A),
    tertiary = Color(0xFFFF8F00),
    error = Color(0xFFFF6F61), errorContainer = Color(0xFFFFE3E0), onErrorContainer = Color(0xFF8C1D18),
    background = Color(0xFFF4F6FA), onBackground = Color(0xFF1B1E2B),
    surface = Color.White, onSurface = Color(0xFF1B1E2B),
    surfaceVariant = Color(0xFFE8ECF4), outlineVariant = Color(0xFFDDE2EE)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9FA8DA), onPrimary = Color(0xFF111633),
    secondary = Color(0xFF4DB6AC), secondaryContainer = Color(0xFF1F3F3B), onSecondaryContainer = Color(0xFFCDEFEA),
    tertiary = Color(0xFFFFB74D),
    error = Color(0xFFFF8A80), errorContainer = Color(0xFF4A2220), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10131F), surface = Color(0xFF1A1F33),
    surfaceVariant = Color(0xFF252B45), outlineVariant = Color(0xFF2E3552)
)

private val AppTypography = Typography().copy(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun HwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            HwTheme {
                Surface(color = MaterialTheme.colorScheme.background) { Nav(vm) }
            }
        }
    }
}

@Composable
fun Nav(vm: VM) {
    val s by vm.settings.collectAsState()
    val cur = s ?: return
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (cur.onboarded) "main" else "onboarding",
        enterTransition = { slideInHorizontally { w -> w / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { w -> w / 4 } + fadeOut() }
    ) {
        composable("onboarding") {
            Onboarding(
                vm = vm,
                onMesh = { nav.navigate("mesh") },
                onFinish = {
                    vm.finishOnboarding()
                    nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                }
            )
        }
        composable("main") { MainScreen(vm, { nav.navigate("archive") }, { nav.navigate("settings") }) }
        composable("archive") { ArchiveScreen(vm) { nav.popBackStack() } }
        composable("settings") {
            SettingsScreen(vm, { nav.popBackStack() }, { nav.navigate("mesh") }, { nav.navigate("apps") })
        }
        composable("apps") { AppsSettings(vm) { nav.popBackStack() } }
        composable("mesh") { MeshScreen { token -> vm.connectMesh(token); nav.popBackStack() } }
    }
}
