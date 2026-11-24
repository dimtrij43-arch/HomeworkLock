package com.hwlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private val Light = lightColorScheme(primary = Color(0xFF3F51B5), secondary = Color(0xFF00897B),
    tertiary = Color(0xFFFF8F00), background = Color(0xFFF6F7FB), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAF6))
private val Dark = darkColorScheme(primary = Color(0xFF9FA8DA), secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFB74D), background = Color(0xFF10131F), surface = Color(0xFF1A1F33),
    surfaceVariant = Color(0xFF252B45))

@Composable
fun HwTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HwTheme { Surface(color = MaterialTheme.colorScheme.background) { Nav(vm) } } }
    }
}

@Composable
fun Nav(vm: VM) {
    val s by vm.settings.collectAsState()
    val cur = s ?: return
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (cur.onboarded) "main" else "onboarding",
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() }) {
        composable("onboarding") {
            Onboarding(vm, onMesh = { nav.navigate("mesh") },
                onFinish = { vm.finishOnboarding(); nav.navigate("main") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("main") { MainScreen(vm, { nav.navigate("archive") }, { nav.navigate("settings") }) }
        composable("archive") { ArchiveScreen(vm) { nav.popBackStack() } }
        composable("settings") { SettingsScreen(vm, { nav.popBackStack() }, { nav.navigate("mesh") }, { nav.navigate("apps") }) }
        composable("apps") { AppsSettings(vm) { nav.popBackStack() } }
        composable("mesh") { MeshScreen { token -> vm.connectMesh(token); nav.popBackStack() } }
    }
}
