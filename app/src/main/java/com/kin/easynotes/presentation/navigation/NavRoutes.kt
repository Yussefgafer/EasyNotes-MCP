package com.kin.easynotes.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kin.easynotes.presentation.screens.settings.MainSettings
import com.kin.easynotes.presentation.screens.settings.model.SettingsViewModel
import com.kin.easynotes.presentation.screens.settings.settings.AboutScreen
import com.kin.easynotes.presentation.screens.settings.settings.AiIntegrationScreen
import com.kin.easynotes.presentation.screens.settings.settings.CloudScreen
import com.kin.easynotes.presentation.screens.settings.settings.ColorStylesScreen
import com.kin.easynotes.presentation.screens.settings.settings.LanguageScreen
import com.kin.easynotes.presentation.screens.settings.settings.MarkdownScreen
import com.kin.easynotes.presentation.screens.settings.settings.PrivacyScreen
import com.kin.easynotes.presentation.screens.settings.settings.SupportScreen
import com.kin.easynotes.presentation.screens.settings.settings.ToolsScreen

enum class ActionType {
    PASSCODE,
    FINGERPRINT,
    PATTERN
}

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Edit : NavRoutes("edit/{id}/{encrypted}") {
        fun createRoute(id: Int, encrypted : Boolean) = "edit/$id/$encrypted"
    }
    data object Terms : NavRoutes("terms")
    data object Settings : NavRoutes("settings")
    data object ColorStyles : NavRoutes("settings/color_styles")
    data object Language : NavRoutes("settings/language")
    data object Cloud : NavRoutes("settings/cloud")
    data object Privacy : NavRoutes("settings/privacy")
    data object Markdown : NavRoutes("settings/markdown")
    data object Tools : NavRoutes("settings/tools")
    data object History : NavRoutes("settings/history")
    data object Widgets : NavRoutes("settings/widgets")
    data object About : NavRoutes("settings/about")
    data object Support : NavRoutes("settings/support")
    data object AiIntegration : NavRoutes("settings/ai_integration")
    data object LockScreen : NavRoutes("settings/lock/{type}") {
        fun createRoute(action: ActionType?) = "settings/lock/$action"
    }
}

val settingScreens = mapOf<String, @Composable (SettingsViewModel, NavController) -> Unit>(
    NavRoutes.Settings.route to { vm, nc -> MainSettings(vm, nc) },
    NavRoutes.ColorStyles.route to { vm, nc -> ColorStylesScreen(nc, vm) },
    NavRoutes.Language.route to { vm, nc -> LanguageScreen(nc, vm) },
    NavRoutes.Cloud.route to { vm, nc -> CloudScreen(nc, vm) },
    NavRoutes.Privacy.route to { vm, nc -> PrivacyScreen(nc, vm) },
    NavRoutes.Markdown.route to { vm, nc -> MarkdownScreen(nc, vm) },
    NavRoutes.Tools.route to { vm, nc -> ToolsScreen(nc, vm) },
    NavRoutes.About.route to { vm, nc -> AboutScreen(nc, vm) },
    NavRoutes.Support.route to { vm, nc -> SupportScreen(nc, vm) },
    NavRoutes.AiIntegration.route to { vm, nc -> AiIntegrationScreen(nc, vm) }
)
