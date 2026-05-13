package com.rainyembedding.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import com.rainyembedding.app.ui.navigation.Screen
import com.rainyembedding.app.ui.screen.*
import com.rainyembedding.app.ui.theme.RainyEmbeddingTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无论授权与否都不影响功能 */ }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 用户可能同意或拒绝，不影响后续 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // HyperOS/MIUI 首次启动引导关闭电池优化
        requestBatteryOptimizationIfNeeded()

        enableEdgeToEdge()
        setContent {
            RainyEmbeddingTheme {
                MainScreen()
            }
        }
    }

    private fun requestBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        // 仅在 HyperOS/MIUI 上主动引导
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (_: Exception) {
            // 部分设备可能不支持此 Intent，静默忽略
        }
    }
}

private fun Modifier.blockAllTouchWhenHidden(visible: Boolean): Modifier {
    if (visible) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentTab by remember { mutableStateOf(Screen.Dashboard.route) }

    val screens = listOf(
        Screen.Dashboard,
        Screen.Models,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentTab == screen.route,
                        onClick = { currentTab = screen.route }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            for (screen in screens.sortedBy { it.route == currentTab }) {
                val isVisible = screen.route == currentTab
                key(screen.route) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isVisible) 1f else 0f }
                            .blockAllTouchWhenHidden(isVisible)
                    ) {
                        when (screen.route) {
                            Screen.Dashboard.route -> DashboardScreen(isVisible = isVisible)
                            Screen.Models.route -> ModelManagerScreen(isVisible = isVisible)
                            Screen.Settings.route -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RainyEmbeddingAppPreview() {
    RainyEmbeddingTheme {
        MainScreen()
    }
}