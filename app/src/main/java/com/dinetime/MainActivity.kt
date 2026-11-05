package com.dinetime

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dinetime.ui.DetailKey
import com.dinetime.ui.DevKey
import com.dinetime.ui.EditorKey
import com.dinetime.ui.HomeKey
import com.dinetime.ui.RemindersKey
import com.dinetime.ui.SettingsKey
import com.dinetime.ui.detail.DetailScreen
import com.dinetime.ui.dev.DevPanelScreen
import com.dinetime.ui.editor.EditorScreen
import com.dinetime.ui.home.HomeScreen
import com.dinetime.ui.rememberContainer
import com.dinetime.ui.settings.RemindersScreen
import com.dinetime.ui.settings.SettingsScreen
import com.dinetime.ui.theme.DineTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by rememberContainer().settings.settings.collectAsStateWithLifecycle()
            DineTimeTheme(dynamicColor = settings.dynamicColor) {
                NotificationPermission()
                DineTimeNav()
            }
        }
    }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun DineTimeNav() {
    val backStack = rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeKey> {
                    HomeScreen(
                        onLog = { backStack.add(EditorKey(plan = false)) },
                        onPlan = { backStack.add(EditorKey(plan = true)) },
                        onOpen = { backStack.add(DetailKey(it)) },
                        onReminders = { backStack.add(RemindersKey) },
                        onSettings = { backStack.add(SettingsKey) },
                        onDev = { backStack.add(DevKey) },
                    )
                }
                entry<EditorKey> { key ->
                    EditorScreen(key.plan, key.mealId, onDone = { backStack.removeLastOrNull() })
                }
                entry<DetailKey> { key ->
                    DetailScreen(
                        key.mealId,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { plan, id -> backStack.add(EditorKey(plan, id)) },
                    )
                }
                entry<RemindersKey> { RemindersScreen({ backStack.removeLastOrNull() }, { backStack.add(DetailKey(it)) }) }
                entry<SettingsKey> { SettingsScreen { backStack.removeLastOrNull() } }
                entry<DevKey> { DevPanelScreen { backStack.removeLastOrNull() } }
            },
    )
}
