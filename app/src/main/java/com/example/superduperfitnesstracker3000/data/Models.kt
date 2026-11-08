package com.example.superduperfitnesstracker3000.data

import java.time.LocalDate

data class WeightEntry(val date: LocalDate, val kg: Double)

data class Nutrition(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
) {
    operator fun plus(other: Nutrition) = Nutrition(
        calories + other.calories,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
        fiber + other.fiber,
        sugar + other.sugar,
        sodiumMg + other.sodiumMg,
    )

    companion object {
        val ZERO = Nutrition()
    }
}

/** A food item the user ate. [nutrition] is null until it has been calculated by Gemini (or entered by hand). */
data class FoodEntry(
    val id: Long,
    val date: LocalDate,
    val name: String,
    val amount: String,
    val nutrition: Nutrition?,
    val note: String?,
    val createdAt: Long,
) {
    val isCalculated get() = nutrition != null
}

enum class ChatRole { USER, MODEL }

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val createdAt: Long,
    /** For coach replies: a short note of what was saved (profile changes, memories, weight). */
    val savedNote: String? = null,
)

data class WaterEntry(val id: Long, val date: LocalDate, val ml: Int, val createdAt: Long)

/** A durable fact the coach remembers about the user (diet, allergies, schedule…). */
data class CoachMemory(val id: Long, val text: String, val createdAt: Long)

data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 90,
    /** Minutes after midnight. */
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
)

data class UserProfile(
    val heightCm: Double? = null,
    val age: Int? = null,
    val sex: String? = null,
    val activityLevel: String? = null,
    val goalWeightKg: Double? = null,
    val calorieTarget: Int? = null,
    val proteinTarget: Int? = null,
    val waterTargetMl: Int? = null,
    val notes: String = "",
) {
    val effectiveWaterTargetMl get() = waterTargetMl ?: DEFAULT_WATER_TARGET_ML

    companion object {
        const val DEFAULT_WATER_TARGET_ML = 2500
    }
}

data class AppSettings(
    val apiKeys: List<String> = emptyList(),
    val model: String = DEFAULT_MODEL,
    val profile: UserProfile = UserProfile(),
    val reminders: ReminderSettings = ReminderSettings(),
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
