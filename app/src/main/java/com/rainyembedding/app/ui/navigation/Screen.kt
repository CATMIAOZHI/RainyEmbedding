package com.rainyembedding.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * RainyEmbedding 底部导航路由定义
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : Screen("dashboard", "主控台", Icons.Filled.Home)
    data object Models : Screen("models", "模型", Icons.Filled.CloudDownload)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}
