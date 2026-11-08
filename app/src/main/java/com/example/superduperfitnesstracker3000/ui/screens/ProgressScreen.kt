package com.example.superduperfitnesstracker3000.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.superduperfitnesstracker3000.data.FoodEntry
import com.example.superduperfitnesstracker3000.data.Stats
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.data.WaterEntry
import com.example.superduperfitnesstracker3000.data.WeightEntry
import com.example.superduperfitnesstracker3000.ui.theme.Accent
import com.example.superduperfitnesstracker3000.ui.components.ChartPoint
import com.example.superduperfitnesstracker3000.ui.components.DailyBarChart
import com.example.superduperfitnesstracker3000.ui.components.EmptyHint
import com.example.superduperfitnesstracker3000.ui.components.SectionCard
import com.example.superduperfitnesstracker3000.ui.components.StatTile
import com.example.superduperfitnesstracker3000.ui.components.WeightLineChart
import com.example.superduperfitnesstracker3000.ui.components.fmt0
import com.example.superduperfitnesstracker3000.ui.components.fmt1
import com.example.superduperfitnesstracker3000.ui.components.fmtSigned
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Range(val label: String, val days: Long?) {
    W2("2W", 14), M1("1M", 30), M3("3M", 90), M6("6M", 182), Y1("1Y", 365), ALL("All", null)
}

private val weekFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Composable
fun ProgressScreen(
    weights: List<WeightEntry>,
    foods: List<FoodEntry>,
    water: List<WaterEntry>,
    profile: UserProfile,
    contentPadding: PaddingValues,
) {
    var range by rememberSaveable { mutableStateOf(Range.M1) }
    val today = LocalDate.now()
    val earliest = listOfNotNull(weights.firstOrNull()?.date, foods.minOfOrNull { it.date }).minOrNull() ?: today
    val from = range.days?.let { today.minusDays(it - 1) } ?: earliest

    val inRange = weights.filter { it.date >= from }
    val avg = Stats.movingAverage(weights).filter { it.date >= from }
    val days = Stats.dailyNutrition(foods).filter { it.date >= from && it.hasData }
    // Bars get too thin past ~120 days; show the most recent part only.
    val barFrom = maxOf(from, today.minusDays(119))
    val barDays = days.filter { it.date >= barFrom }
    val weeks = Stats.weeklySummaries(weights, foods).filter { it.weekStart >= Stats.weekStart(from) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) })
                }
            }
        }

        if (weights.isEmpty() && foods.isEmpty()) {
            item { EmptyHint("Log some weigh-ins and meals to see your progress here.") }
            return@LazyColumn
        }

        item {
            val trend = Stats.trendPerWeek(inRange)
            val change = if (avg.size >= 2) avg.last().kg - avg.first().kg else null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Change", change?.let { "${fmtSigned(it)} kg" } ?: "—", Modifier.weight(1f), sub = "7-day avg, in range")
                StatTile("Trend", trend?.let { fmtSigned(it, 2) } ?: "—", Modifier.weight(1f), sub = "kg / week", accent = Accent.water)
                val goal = profile.goalWeightKg
                val current = avg.lastOrNull()?.kg
                val eta = if (goal != null && current != null && trend != null && trend != 0.0 && (goal - current) / trend > 0)
                    "${fmt0((goal - current) / trend)} wks" else "—"
                StatTile("Goal ETA", eta, Modifier.weight(1f), sub = goal?.let { "to ${fmt1(it)} kg" } ?: "set in settings", accent = Accent.calories)
            }
        }

        item {
            SectionCard(title = "Weight") {
                WeightLineChart(
                    raw = inRange.map { ChartPoint(it.date, it.kg) },
                    average = avg.map { ChartPoint(it.date, it.kg) },
                    goal = profile.goalWeightKg,
                )
            }
        }

        item {
            SectionCard(title = "Calories") {
                if (days.isNotEmpty()) {
                    Text(
                        "Average ${fmt0(days.map { it.total.calories }.average())} kcal/day over ${days.size} logged day(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DailyBarChart(
                    points = barDays.map { ChartPoint(it.date, it.total.calories) },
                    from = barFrom, to = today,
                    target = profile.calorieTarget?.toDouble(),
                    unit = "kcal",
                    color = Accent.calories,
                )
            }
        }

        item {
            SectionCard(title = "Protein") {
                if (days.isNotEmpty()) {
                    Text(
                        "Average ${fmt0(days.map { it.total.protein }.average())} g/day",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DailyBarChart(
                    points = barDays.map { ChartPoint(it.date, it.total.protein) },
                    from = barFrom, to = today,
                    target = profile.proteinTarget?.toDouble(),
                    unit = "g protein",
                    color = Accent.protein,
                )
            }
        }

        val waterDays = water.filter { it.date >= barFrom }.groupBy { it.date }.map { (d, v) -> ChartPoint(d, v.sumOf { it.ml }.toDouble()) }
        item {
            SectionCard(title = "Water") {
                if (waterDays.isNotEmpty()) {
                    Text(
                        "Average ${fmt0(waterDays.map { it.value }.average())} ml/day over ${waterDays.size} logged day(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DailyBarChart(
                    points = waterDays,
                    from = barFrom, to = today,
                    target = profile.effectiveWaterTargetMl.toDouble(),
                    unit = "ml",
                    emptyText = "No water logged in this range",
                    color = Accent.water,
                )
            }
        }

        if (days.isNotEmpty()) {
            item {
                SectionCard(title = "Average macros") {
                    val n = days.size
                    val total = days.map { it.total }.reduce { a, b -> a + b }
                    val pCal = total.protein * 4
                    val cCal = total.carbs * 4
                    val fCal = total.fat * 9
                    val sum = (pCal + cCal + fCal).takeIf { it > 0 } ?: 1.0
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Protein", "${fmt0(total.protein / n)} g", Modifier.weight(1f), sub = "${fmt0(pCal / sum * 100)}% kcal", accent = Accent.protein)
                        StatTile("Carbs", "${fmt0(total.carbs / n)} g", Modifier.weight(1f), sub = "${fmt0(cCal / sum * 100)}% kcal", accent = Accent.calories)
                        StatTile("Fat", "${fmt0(total.fat / n)} g", Modifier.weight(1f), sub = "${fmt0(fCal / sum * 100)}% kcal", accent = Accent.water)
                    }
                    Text(
                        "Fiber ${fmt0(total.fiber / n)} g · Sugar ${fmt0(total.sugar / n)} g · Sodium ${fmt0(total.sodiumMg / n)} mg per day",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (weeks.isNotEmpty()) {
            item {
                SectionCard(title = "Weekly averages") {
                    WeekRow("Week of", "Weight", "Δ", "kcal", "Protein", header = true)
                    HorizontalDivider()
                    weeks.forEachIndexed { i, w ->
                        val prev = weeks.drop(i + 1).firstOrNull { it.avgWeight != null }?.avgWeight
                        WeekRow(
                            w.weekStart.format(weekFmt),
                            w.avgWeight?.let { fmt1(it) } ?: "—",
                            if (w.avgWeight != null && prev != null) fmtSigned(w.avgWeight - prev) else "",
                            w.avgCalories?.let { fmt0(it) } ?: "—",
                            w.avgProtein?.let { "${fmt0(it)} g" } ?: "—",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(a: String, b: String, c: String, d: String, e: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(a, style = style, modifier = Modifier.weight(1.2f))
        Text(b, style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(c, style = style, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End,
            color = if (c.startsWith("-")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(d, style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(e, style = style, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
    }
}
