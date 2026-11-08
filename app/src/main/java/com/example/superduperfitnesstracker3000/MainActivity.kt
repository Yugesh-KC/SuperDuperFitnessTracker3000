package com.example.superduperfitnesstracker3000

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superduperfitnesstracker3000.ui.AppViewModel
import com.example.superduperfitnesstracker3000.ui.components.ApiKeyDialog
import com.example.superduperfitnesstracker3000.ui.screens.CoachScreen
import com.example.superduperfitnesstracker3000.ui.screens.FoodScreen
import com.example.superduperfitnesstracker3000.ui.screens.ProgressScreen
import com.example.superduperfitnesstracker3000.ui.screens.SUGGEST_NEXT_MEAL
import com.example.superduperfitnesstracker3000.ui.screens.SettingsScreen
import com.example.superduperfitnesstracker3000.ui.screens.WaterScreen
import com.example.superduperfitnesstracker3000.ui.screens.WeightScreen
import com.example.superduperfitnesstracker3000.ui.theme.Accent
import com.example.superduperfitnesstracker3000.ui.theme.SuperDuperFitnessTracker3000Theme

class MainActivity : ComponentActivity() {
    /** Tab requested by a notification tap. */
    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
        setContent {
            SuperDuperFitnessTracker3000Theme {
                FitnessApp(requestedTab = requestedTab, onTabRequestHandled = { requestedTab = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}

private enum class Tab(val title: String, val icon: ImageVector, val accent: Color) {
    Weight("Weight", Icons.Filled.MonitorWeight, Accent.weight),
    Food("Food", Icons.Filled.Restaurant, Accent.calories),
    Water("Water", Icons.Filled.WaterDrop, Accent.water),
    Progress("Progress", Icons.AutoMirrored.Filled.ShowChart, Accent.weight),
    Coach("Coach", Icons.Filled.AutoAwesome, Accent.protein),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessApp(
    requestedTab: String? = null,
    onTabRequestHandled: () -> Unit = {},
    vm: AppViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Weight) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val foods by vm.foods.collectAsStateWithLifecycle()
    val water by vm.water.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val calculating by vm.calculating.collectAsStateWithLifecycle()
    val chatLoading by vm.chatLoading.collectAsStateWithLifecycle()
    val keyTests by vm.keyTestResults.collectAsStateWithLifecycle()
    val models by vm.availableModels.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Water may have been logged from a notification while we were in the background.
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose { }
    }

    LaunchedEffect(requestedTab) {
        Tab.entries.firstOrNull { it.name == requestedTab }?.let {
            tab = it
            showSettings = false
        }
        if (requestedTab != null) onTabRequestHandled()
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    // AI actions wait here while the user is asked for an API key the first time.
    var pendingAiAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun withApiKey(action: () -> Unit) {
        if (settings.apiKeys.isEmpty()) pendingAiAction = action else action()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showSettings) "Settings" else if (tab == Tab.Coach) "AI Coach" else tab.title) },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (!showSettings && tab == Tab.Coach && chat.isNotEmpty()) {
                        IconButton(onClick = vm::clearChat) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat") }
                    }
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = !showSettings && t == tab,
                        onClick = { tab = t; showSettings = false },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = t.accent,
                            selectedTextColor = t.accent,
                            indicatorColor = t.accent.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                settings = settings,
                keyTestResults = keyTests,
                availableModels = models,
                latestWeightKg = weights.lastOrNull()?.kg,
                onAddKeys = vm::addApiKeys,
                onRemoveKey = vm::removeApiKey,
                onTestKeys = vm::testKeys,
                onLoadModels = vm::loadModels,
                onSetModel = vm::setModel,
                onSaveProfile = vm::saveProfile,
                onClearChat = vm::clearChat,
                memories = memories,
                onAddMemory = vm::addMemory,
                onDeleteMemory = vm::deleteMemory,
                contentPadding = padding,
            )
            return@Scaffold
        }
        when (tab) {
            Tab.Weight -> WeightScreen(
                weights = weights,
                profile = settings.profile,
                onSave = vm::saveWeight,
                onDelete = vm::deleteWeight,
                contentPadding = padding,
            )
            Tab.Food -> FoodScreen(
                foods = foods,
                profile = settings.profile,
                calculating = calculating,
                onAdd = vm::addFood,
                onUpdate = vm::updateFood,
                onDelete = vm::deleteFood,
                onCalculate = { withApiKey(vm::calculatePending) },
                onSuggestNext = {
                    withApiKey {
                        tab = Tab.Coach
                        vm.sendChat(SUGGEST_NEXT_MEAL)
                    }
                },
                contentPadding = padding,
            )
            Tab.Water -> WaterScreen(
                water = water,
                profile = settings.profile,
                reminders = settings.reminders,
                onAdd = vm::addWater,
                onDelete = vm::deleteWater,
                onSaveTarget = { vm.saveProfile(settings.profile.copy(waterTargetMl = it)) },
                onSaveReminders = vm::saveReminders,
                onTestReminder = vm::testReminder,
                contentPadding = padding,
            )
            Tab.Progress -> ProgressScreen(
                weights = weights,
                foods = foods,
                water = water,
                profile = settings.profile,
                contentPadding = padding,
            )
            Tab.Coach -> CoachScreen(
                messages = chat,
                loading = chatLoading,
                onSend = { text -> withApiKey { vm.sendChat(text) } },
                onRetry = { withApiKey(vm::retryChat) },
                contextPreview = vm::coachContextPreview,
                contentPadding = padding,
            )
        }
    }

    pendingAiAction?.let { action ->
        ApiKeyDialog(
            onDismiss = { pendingAiAction = null },
            onSave = { keys ->
                vm.addApiKeys(keys)
                pendingAiAction = null
                action()
            },
        )
    }
}
