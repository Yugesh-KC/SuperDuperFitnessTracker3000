package com.example.superduperfitnesstracker3000.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.superduperfitnesstracker3000.data.Stats
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.data.WeightEntry
import com.example.superduperfitnesstracker3000.ui.theme.Accent
import com.example.superduperfitnesstracker3000.ui.components.ChartPoint
import com.example.superduperfitnesstracker3000.ui.components.DateNavigator
import com.example.superduperfitnesstracker3000.ui.components.EmptyHint
import com.example.superduperfitnesstracker3000.ui.components.SectionCard
import com.example.superduperfitnesstracker3000.ui.components.StatTile
import com.example.superduperfitnesstracker3000.ui.components.WeightLineChart
import com.example.superduperfitnesstracker3000.ui.components.fmt1
import com.example.superduperfitnesstracker3000.ui.components.fmtSigned
import com.example.superduperfitnesstracker3000.ui.components.pretty
import com.example.superduperfitnesstracker3000.ui.components.toNumberOrNull
import java.time.LocalDate

@Composable
fun WeightScreen(
    weights: List<WeightEntry>,
    profile: UserProfile,
    onSave: (LocalDate, Double) -> Unit,
    onDelete: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var input by rememberSaveable { mutableStateOf("") }
    val existing = weights.firstOrNull { it.date == date }
    val focus = LocalFocusManager.current

    // When switching day, pre-fill with that day's weight (if logged) so it can be edited.
    LaunchedEffect(date, existing) { input = existing?.let { fmt1(it.kg) } ?: "" }

    val today = LocalDate.now()
    val latest = weights.lastOrNull()
    val avg7 = Stats.averageBetween(weights, today.minusDays(6), today)
    val prev7 = Stats.averageBetween(weights, today.minusDays(13), today.minusDays(7))
    val recent = weights.filter { it.date >= today.minusDays(29) }

    fun save() {
        val kg = input.toNumberOrNull() ?: return
        if (kg !in 20.0..400.0) return
        onSave(date, kg)
        focus.clearFocus()
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Log your weight") {
                DateNavigator(date, onDateChange = { date = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { v -> input = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        label = { Text("Weight (kg)") },
                        placeholder = { latest?.let { Text(fmt1(it.kg)) } },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )
                    Button(onClick = ::save, enabled = input.toNumberOrNull()?.let { it in 20.0..400.0 } == true) {
                        Text(if (existing != null) "Update" else "Save")
                    }
                }
                if (existing != null) {
                    Text(
                        "Logged ${fmt1(existing.kg)} kg for ${date.pretty().lowercase()} — saving will replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (latest != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Latest", "${fmt1(latest.kg)} kg", Modifier.weight(1f), sub = latest.date.pretty())
                    StatTile("7-day avg", avg7?.let { "${fmt1(it)} kg" } ?: "—", Modifier.weight(1f), accent = Accent.water,
                        sub = if (avg7 != null && prev7 != null) "${fmtSigned(avg7 - prev7)} vs last wk" else null)
                    profile.goalWeightKg?.let { goal ->
                        val ref = avg7 ?: latest.kg
                        StatTile("To goal", "${fmt1(ref - goal)} kg", Modifier.weight(1f), sub = "goal ${fmt1(goal)}", accent = Accent.calories)
                    }
                }
            }
            item {
                SectionCard(title = "Last 30 days") {
                    WeightLineChart(
                        raw = recent.map { ChartPoint(it.date, it.kg) },
                        average = Stats.movingAverage(weights).filter { it.date >= today.minusDays(29) }.map { ChartPoint(it.date, it.kg) },
                        goal = profile.goalWeightKg,
                    )
                }
            }
        }

        item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (weights.isEmpty()) {
            item { EmptyHint("No weigh-ins yet. Step on your scale and log your first weight above.") }
        }
        val reversed = weights.asReversed()
        itemsIndexed(reversed, key = { _, e -> e.date.toEpochDay() }) { idx, entry ->
            val previous = reversed.getOrNull(idx + 1)
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { date = entry.date }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.date.pretty(), style = MaterialTheme.typography.bodyLarge)
                        if (previous != null) {
                            Text(
                                "${fmtSigned(entry.kg - previous.kg)} kg since ${previous.date.pretty().lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("${fmt1(entry.kg)} kg", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onDelete(entry.date) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
