package com.example.superduperfitnesstracker3000.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import com.example.superduperfitnesstracker3000.ui.theme.Accent
import com.example.superduperfitnesstracker3000.data.FoodEntry
import com.example.superduperfitnesstracker3000.data.Nutrition
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.ui.components.DateNavigator
import com.example.superduperfitnesstracker3000.ui.components.EmptyHint
import com.example.superduperfitnesstracker3000.ui.components.SectionCard
import com.example.superduperfitnesstracker3000.ui.components.fmt0
import com.example.superduperfitnesstracker3000.ui.components.toNumberOrNull
import java.time.LocalDate

@Composable
fun FoodScreen(
    foods: List<FoodEntry>,
    profile: UserProfile,
    calculating: Boolean,
    onAdd: (LocalDate, String, String) -> Unit,
    onUpdate: (FoodEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onCalculate: () -> Unit,
    onSuggestNext: () -> Unit,
    contentPadding: PaddingValues,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    val nameFocus = remember { FocusRequester() }

    val dayItems = foods.filter { it.date == date }
    val dayTotal = dayItems.mapNotNull { it.nutrition }.fold(Nutrition.ZERO, Nutrition::plus)
    val pendingAll = foods.count { !it.isCalculated }
    val pendingToday = dayItems.count { !it.isCalculated }

    fun add() {
        if (name.isBlank()) return
        onAdd(date, name, amount)
        name = ""
        amount = ""
        nameFocus.requestFocus()
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateNavigator(date, onDateChange = { date = it }) }

        item {
            SectionCard(title = "Day total") {
                MacroBar("Calories", dayTotal.calories, profile.calorieTarget?.toDouble(), "kcal", Accent.calories)
                MacroBar("Protein", dayTotal.protein, profile.proteinTarget?.toDouble(), "g", Accent.protein)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MacroText("Carbs", dayTotal.carbs, "g")
                    MacroText("Fat", dayTotal.fat, "g")
                    MacroText("Fiber", dayTotal.fiber, "g")
                    MacroText("Sugar", dayTotal.sugar, "g")
                }
                if (pendingToday > 0) {
                    Text(
                        "$pendingToday item(s) not calculated yet — totals are incomplete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (date == LocalDate.now()) {
                    OutlinedButton(onClick = onSuggestNext, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("What should I eat next?")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Add what you ate") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food (e.g. chicken biryani, boiled eggs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (2, 150 g, 1 bowl)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    FilledTonalButton(onClick = ::add, enabled = name.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add")
                    }
                }
                Text(
                    "Add everything first, then calculate all at once — one request for the whole list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (pendingAll > 0 || calculating) {
            item {
                Button(onClick = onCalculate, enabled = !calculating, modifier = Modifier.fillMaxWidth()) {
                    if (calculating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Asking Gemini…")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        val other = pendingAll - pendingToday
                        Text("Calculate $pendingAll item(s)" + if (other > 0) " ($other on other days)" else "")
                    }
                }
            }
        }

        item { Text("Items", style = MaterialTheme.typography.titleMedium) }
        if (dayItems.isEmpty()) {
            item { EmptyHint("Nothing logged for this day yet.") }
        }
        items(dayItems, key = { it.id }) { food ->
            FoodRow(food, onClick = { editing = food }, onDelete = { onDelete(food.id) })
        }
    }

    editing?.let { food ->
        EditFoodDialog(
            food = food,
            onDismiss = { editing = null },
            onSave = { onUpdate(it); editing = null },
        )
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Double?, unit: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
            Text(
                "${fmt0(value)}${target?.let { " / ${fmt0(it)}" } ?: ""} $unit",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (value / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun MacroText(label: String, value: Double, unit: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${fmt0(value)} $unit", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FoodRow(food: FoodEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge)
                if (food.amount.isNotBlank()) {
                    Text(food.amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val n = food.nutrition
                if (n != null) {
                    Text(
                        "${fmt0(n.calories)} kcal · P ${fmt0(n.protein)} g · C ${fmt0(n.carbs)} g · F ${fmt0(n.fat)} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent.calories,
                    )
                    food.note?.let {
                        Text("Assumed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Not calculated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
        HorizontalDivider()
    }
}

/** Edit a food's description or override its nutrition values by hand. */
@Composable
private fun EditFoodDialog(food: FoodEntry, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var name by remember { mutableStateOf(food.name) }
    var amount by remember { mutableStateOf(food.amount) }
    val n = food.nutrition
    val fields = remember {
        listOf("Calories (kcal)", "Protein (g)", "Carbs (g)", "Fat (g)", "Fiber (g)", "Sugar (g)", "Sodium (mg)")
            .zip(listOf(n?.calories, n?.protein, n?.carbs, n?.fat, n?.fiber, n?.sugar, n?.sodiumMg))
            .map { (label, v) -> label to mutableStateOf(v?.let { fmt0(it) } ?: "") }
    }
    val textChanged = name.trim() != food.name || amount.trim() != food.amount

    fun build(clearNutrition: Boolean): FoodEntry {
        val values = fields.map { it.second.value.toNumberOrNull() }
        val nutrition = if (clearNutrition || values[0] == null) null else Nutrition(
            calories = values[0]!!, protein = values[1] ?: 0.0, carbs = values[2] ?: 0.0, fat = values[3] ?: 0.0,
            fiber = values[4] ?: 0.0, sugar = values[5] ?: 0.0, sodiumMg = values[6] ?: 0.0,
        )
        return food.copy(name = name.trim(), amount = amount.trim(), nutrition = nutrition, note = if (nutrition == null) null else food.note)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Food") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    if (textChanged) "You changed the food — save as \"needs recalculation\" to let Gemini redo the numbers."
                    else "Override values manually, or leave Calories empty to recalculate with Gemini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                fields.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, state) ->
                            OutlinedTextField(
                                value = state.value,
                                onValueChange = { state.value = it },
                                label = { Text(label) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(build(clearNutrition = false)) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(build(clearNutrition = true)) }, enabled = name.isNotBlank()) { Text("Needs recalculation") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
