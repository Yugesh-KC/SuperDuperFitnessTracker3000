package com.example.superduperfitnesstracker3000.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DailyNutrition(
    val date: LocalDate,
    val total: Nutrition,
    val itemCount: Int,
    val pendingCount: Int,
) {
    /** True if at least one item that day has nutrition values. */
    val hasData get() = itemCount > pendingCount
}

data class WeeklySummary(
    val weekStart: LocalDate,
    val avgWeight: Double?,
    val weighIns: Int,
    val avgCalories: Double?,
    val avgProtein: Double?,
    val daysLogged: Int,
)

object Stats {

    fun dailyNutrition(foods: List<FoodEntry>): List<DailyNutrition> =
        foods.groupBy { it.date }
            .map { (date, items) ->
                DailyNutrition(
                    date = date,
                    total = items.mapNotNull { it.nutrition }.fold(Nutrition.ZERO, Nutrition::plus),
                    itemCount = items.size,
                    pendingCount = items.count { !it.isCalculated },
                )
            }
            .sortedBy { it.date }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Weekly (Monday-start) averages, newest week first. Only days with calculated food count toward intake averages. */
    fun weeklySummaries(weights: List<WeightEntry>, foods: List<FoodEntry>): List<WeeklySummary> {
        val weightsByWeek = weights.groupBy { weekStart(it.date) }
        val daysByWeek = dailyNutrition(foods).filter { it.hasData }.groupBy { weekStart(it.date) }
        return (weightsByWeek.keys + daysByWeek.keys).distinct().sortedDescending().map { week ->
            val w = weightsByWeek[week].orEmpty()
            val d = daysByWeek[week].orEmpty()
            WeeklySummary(
                weekStart = week,
                avgWeight = w.map { it.kg }.averageOrNull(),
                weighIns = w.size,
                avgCalories = d.map { it.total.calories }.averageOrNull(),
                avgProtein = d.map { it.total.protein }.averageOrNull(),
                daysLogged = d.size,
            )
        }
    }

    /** Time-based moving average: each point is the mean of all weigh-ins in the [windowDays] days ending on that date. */
    fun movingAverage(weights: List<WeightEntry>, windowDays: Long = 7): List<WeightEntry> {
        val sorted = weights.sortedBy { it.date }
        return sorted.map { e ->
            val from = e.date.minusDays(windowDays - 1)
            val window = sorted.filter { it.date in from..e.date }
            WeightEntry(e.date, window.map { it.kg }.average())
        }
    }

    fun averageBetween(weights: List<WeightEntry>, from: LocalDate, to: LocalDate): Double? =
        weights.filter { it.date in from..to }.map { it.kg }.averageOrNull()

    /** Linear-regression slope over the given entries, in kg per week. Needs at least 2 distinct days. */
    fun trendPerWeek(weights: List<WeightEntry>): Double? {
        if (weights.size < 2) return null
        val xs = weights.map { it.date.toEpochDay().toDouble() }
        val ys = weights.map { it.kg }
        val mx = xs.average()
        val my = ys.average()
        val denom = xs.sumOf { (it - mx) * (it - mx) }
        if (denom == 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / denom
        return slope * 7
    }

    fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
