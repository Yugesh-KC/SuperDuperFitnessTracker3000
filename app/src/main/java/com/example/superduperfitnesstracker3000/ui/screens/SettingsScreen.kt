package com.example.superduperfitnesstracker3000.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.superduperfitnesstracker3000.data.AppSettings
import com.example.superduperfitnesstracker3000.data.CoachMemory
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.ui.AppViewModel
import com.example.superduperfitnesstracker3000.ui.components.SectionCard
import com.example.superduperfitnesstracker3000.ui.components.fmt0
import com.example.superduperfitnesstracker3000.ui.components.fmt1
import com.example.superduperfitnesstracker3000.ui.components.maskKey
import com.example.superduperfitnesstracker3000.ui.components.toNumberOrNull

private val sexes = listOf("male", "female", "other")
private val activityLevels = listOf("sedentary", "light", "moderate", "very active")

@Composable
fun SettingsScreen(
    settings: AppSettings,
    keyTestResults: Map<String, String>,
    availableModels: List<String>,
    latestWeightKg: Double?,
    onAddKeys: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
    onTestKeys: () -> Unit,
    onLoadModels: () -> Unit,
    onSetModel: (String) -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onClearChat: () -> Unit,
    memories: List<CoachMemory>,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val uriHandler = LocalUriHandler.current
    var newKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }

    // Keyed on the saved profile so changes made by the coach show up here.
    val p = settings.profile
    var height by rememberSaveable(p) { mutableStateOf(p.heightCm?.let(::fmt0) ?: "") }
    var age by rememberSaveable(p) { mutableStateOf(p.age?.toString() ?: "") }
    var sex by rememberSaveable(p) { mutableStateOf(p.sex) }
    var activity by rememberSaveable(p) { mutableStateOf(p.activityLevel) }
    var goal by rememberSaveable(p) { mutableStateOf(p.goalWeightKg?.let(::fmt1) ?: "") }
    var calories by rememberSaveable(p) { mutableStateOf(p.calorieTarget?.toString() ?: "") }
    var protein by rememberSaveable(p) { mutableStateOf(p.proteinTarget?.toString() ?: "") }
    var waterTarget by rememberSaveable(p) { mutableStateOf(p.waterTargetMl?.toString() ?: "") }
    var notes by rememberSaveable(p) { mutableStateOf(p.notes) }
    var newMemory by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Gemini API keys") {
                Text(
                    "Requests rotate through your keys in turn, and if one hits its rate limit or fails, the next key is tried automatically. Keys stay on this device and are excluded from backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
                    Text("Get a free key at aistudio.google.com")
                }
                if (settings.apiKeys.isEmpty()) {
                    Text("No keys yet.", style = MaterialTheme.typography.bodyMedium)
                }
                settings.apiKeys.forEachIndexed { i, key ->
                    val result = keyTestResults[key]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${maskKey(key)}", style = MaterialTheme.typography.bodyLarge)
                            if (result != null && result != AppViewModel.OK) {
                                Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        when {
                            result == AppViewModel.OK -> Icon(Icons.Filled.CheckCircle, "Works", tint = MaterialTheme.colorScheme.primary)
                            result != null -> Icon(Icons.Filled.Error, "Failed", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onRemoveKey(key) }) { Icon(Icons.Filled.Delete, "Remove key") }
                    }
                }
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Add key(s) — paste one or several") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAddKeys(newKey); newKey = "" }, enabled = newKey.isNotBlank()) { Text("Add") }
                    OutlinedButton(onClick = onTestKeys, enabled = settings.apiKeys.isNotEmpty()) { Text("Test all keys") }
                }
            }
        }

        item {
            SectionCard(title = "Model") {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Gemini model id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSetModel(model) }, enabled = model.trim() != settings.model) { Text("Use this model") }
                    OutlinedButton(onClick = onLoadModels, enabled = settings.apiKeys.isNotEmpty()) { Text("Load available") }
                }
                if (availableModels.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableModels.forEach { m ->
                            FilterChip(selected = m == settings.model, onClick = { model = m; onSetModel(m) }, label = { Text(m) })
                        }
                    }
                }
                Text(
                    "A fast \"flash\" model is plenty for food estimates and coaching, and has the most generous free quota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Your profile") {
                Text(
                    "Used for goal lines and targets, and shared with the AI coach for context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Height (cm)", height, { height = it }, Modifier.weight(1f))
                    NumberField("Age", age, { age = it }, Modifier.weight(1f), decimal = false)
                }
                Text("Sex", style = MaterialTheme.typography.labelLarge)
                ChipRow(sexes, sex) { sex = it }
                Text("Activity level", style = MaterialTheme.typography.labelLarge)
                ChipRow(activityLevels, activity) { activity = it }
                NumberField("Goal weight (kg)", goal, { goal = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Calories / day", calories, { calories = it }, Modifier.weight(1f), decimal = false)
                    NumberField("Protein g / day", protein, { protein = it }, Modifier.weight(1f), decimal = false)
                }
                NumberField("Water ml / day (default ${UserProfile.DEFAULT_WATER_TARGET_ML})", waterTarget, { waterTarget = it }, Modifier.fillMaxWidth(), decimal = false)
                val suggestion = suggestTargets(height.toNumberOrNull(), age.toIntOrNull(), sex, activity, latestWeightKg, goal.toNumberOrNull())
                if (suggestion != null) {
                    TextButton(onClick = { calories = suggestion.first.toString(); protein = suggestion.second.toString() }) {
                        Text("Suggest: ${suggestion.first} kcal, ${suggestion.second} g protein")
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes for the coach (diet, training, injuries…)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(onClick = {
                    onSaveProfile(
                        UserProfile(
                            heightCm = height.toNumberOrNull(),
                            age = age.toIntOrNull(),
                            sex = sex,
                            activityLevel = activity,
                            goalWeightKg = goal.toNumberOrNull(),
                            calorieTarget = calories.toNumberOrNull()?.toInt(),
                            proteinTarget = protein.toNumberOrNull()?.toInt(),
                            waterTargetMl = waterTarget.toIntOrNull()?.takeIf { it in 500..8000 },
                            notes = notes.trim(),
                        )
                    )
                }) { Text("Save profile") }
            }
        }

        item {
            SectionCard(title = "Coach memory") {
                Text(
                    "Facts the coach remembers about you. It adds to this list when you tell it things in chat; you can also add or delete them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (memories.isEmpty()) Text("Nothing remembered yet.", style = MaterialTheme.typography.bodyMedium)
                memories.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${m.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteMemory(m.id) }) { Icon(Icons.Filled.Delete, "Forget") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newMemory,
                        onValueChange = { newMemory = it },
                        label = { Text("e.g. I'm vegetarian") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onAddMemory(newMemory); newMemory = "" }, enabled = newMemory.isNotBlank()) { Text("Add") }
                }
                OutlinedButton(onClick = onClearChat) { Text("Clear chat history") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
private fun ChipRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(if (o == selected) null else o) }, label = { Text(o) })
        }
    }
}

/**
 * Mifflin-St Jeor BMR × activity factor, adjusted ~400 kcal toward the goal;
 * protein ≈ 1.8 g per kg of current weight.
 */
private fun suggestTargets(
    heightCm: Double?, age: Int?, sex: String?, activity: String?, weightKg: Double?, goalKg: Double?,
): Pair<Int, Int>? {
    if (heightCm == null || age == null || weightKg == null || sex == null) return null
    val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + when (sex) {
        "male" -> 5.0
        "female" -> -161.0
        else -> -78.0
    }
    val factor = when (activity) {
        "sedentary" -> 1.2
        "light" -> 1.375
        "very active" -> 1.725
        else -> 1.55
    }
    val maintenance = bmr * factor
    val adjust = when {
        goalKg == null -> 0.0
        goalKg < weightKg - 0.5 -> -400.0
        goalKg > weightKg + 0.5 -> 300.0
        else -> 0.0
    }
    val kcal = ((maintenance + adjust) / 50).toInt() * 50
    val protein = (weightKg * 1.8 / 5).toInt() * 5
    return kcal to protein
}
