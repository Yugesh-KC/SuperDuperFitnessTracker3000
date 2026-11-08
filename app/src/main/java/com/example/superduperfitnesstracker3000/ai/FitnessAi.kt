package com.example.superduperfitnesstracker3000.ai

import com.example.superduperfitnesstracker3000.data.AppSettings
import com.example.superduperfitnesstracker3000.data.ChatMessage
import com.example.superduperfitnesstracker3000.data.ChatRole
import com.example.superduperfitnesstracker3000.data.CoachMemory
import com.example.superduperfitnesstracker3000.data.WaterEntry
import com.example.superduperfitnesstracker3000.data.FoodEntry
import com.example.superduperfitnesstracker3000.data.Nutrition
import com.example.superduperfitnesstracker3000.data.Stats
import com.example.superduperfitnesstracker3000.data.WeightEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Builds the Gemini prompts for nutrition estimation and the coach chat. */
class FitnessAi(private val client: GeminiClient) {

    /**
     * Estimates nutrition for all [items], batched into as few requests as possible.
     * Returns food id -> (nutrition, assumption note).
     */
    suspend fun analyzeFoods(items: List<FoodEntry>): Map<Long, Pair<Nutrition, String?>> {
        val results = mutableMapOf<Long, Pair<Nutrition, String?>>()
        for (chunk in items.chunked(BATCH_SIZE)) {
            val input = JSONArray()
            chunk.forEach {
                input.put(JSONObject().put("id", it.id).put("food", it.name).put("amount", it.amount.ifBlank { "1 typical serving" }))
            }
            val response = client.generate(
                contents = GeminiClient.userContent(input.toString()),
                systemInstruction = NUTRITION_PROMPT,
                responseSchema = nutritionSchema,
            )
            val array = JSONObject(response).getJSONArray("items")
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.optLong("id", -1)
                if (chunk.none { it.id == id }) continue
                results[id] = Nutrition(
                    calories = o.optDouble("calories", 0.0),
                    protein = o.optDouble("protein_g", 0.0),
                    carbs = o.optDouble("carbs_g", 0.0),
                    fat = o.optDouble("fat_g", 0.0),
                    fiber = o.optDouble("fiber_g", 0.0),
                    sugar = o.optDouble("sugar_g", 0.0),
                    sodiumMg = o.optDouble("sodium_mg", 0.0),
                ) to o.optString("assumption").takeIf { it.isNotBlank() }
            }
        }
        return results
    }

    /** What the coach replied, plus anything it wants saved. */
    data class CoachReply(
        val text: String,
        val profileUpdates: ProfileUpdates,
        val remember: List<String>,
        val logWeightKg: Double?,
    )

    data class ProfileUpdates(
        val heightCm: Double? = null,
        val age: Int? = null,
        val sex: String? = null,
        val activityLevel: String? = null,
        val goalWeightKg: Double? = null,
        val calorieTarget: Int? = null,
        val proteinTarget: Int? = null,
        val waterTargetMl: Int? = null,
    )

    suspend fun chat(history: List<ChatMessage>, data: CoachData): CoachReply {
        val contents = JSONArray()
        history.takeLast(MAX_HISTORY).forEach { m ->
            contents.put(
                JSONObject()
                    .put("role", if (m.role == ChatRole.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
            )
        }
        val system = COACH_PROMPT + "\n\n" + buildContext(data)
        val raw = client.generate(contents, systemInstruction = system, responseSchema = coachSchema, temperature = 0.7)
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return CoachReply(raw, ProfileUpdates(), emptyList(), null)
        val p = json.optJSONObject("profile_updates") ?: JSONObject()
        fun d(key: String) = if (p.has(key) && !p.isNull(key)) p.optDouble(key).takeIf { !it.isNaN() && it > 0 } else null
        fun s(key: String, allowed: List<String>) = p.optString(key).lowercase().takeIf { it in allowed }
        val remember = json.optJSONArray("remember")?.let { a -> List(a.length()) { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
        return CoachReply(
            text = json.optString("reply").ifBlank { raw },
            profileUpdates = ProfileUpdates(
                heightCm = d("height_cm")?.takeIf { it in 80.0..250.0 },
                age = d("age")?.toInt()?.takeIf { it in 10..110 },
                sex = s("sex", listOf("male", "female", "other")),
                activityLevel = s("activity_level", listOf("sedentary", "light", "moderate", "very active")),
                goalWeightKg = d("goal_weight_kg")?.takeIf { it in 25.0..300.0 },
                calorieTarget = d("calorie_target")?.toInt()?.takeIf { it in 800..6000 },
                proteinTarget = d("protein_target_g")?.toInt()?.takeIf { it in 20..400 },
                waterTargetMl = d("water_target_ml")?.toInt()?.takeIf { it in 500..8000 },
            ),
            remember = remember,
            logWeightKg = if (json.has("log_weight_kg") && !json.isNull("log_weight_kg"))
                json.optDouble("log_weight_kg").takeIf { it in 20.0..400.0 } else null,
        )
    }

    /** Everything the coach gets to see. */
    data class CoachData(
        val settings: AppSettings,
        val weights: List<WeightEntry>,
        val foods: List<FoodEntry>,
        val water: List<WaterEntry>,
        val memories: List<CoachMemory>,
    )

    /**
     * A compact, token-cheap summary of the user's data: profile, remembered facts, weight trend,
     * weekly averages for the last 8 weeks, daily nutrition for the last 14 days, today's food, what's
     * left of today's targets, and water intake.
     */
    fun buildContext(data: CoachData): String = buildString {
        val (settings, weights, foods, water, memories) = data
        val today = LocalDate.now()
        val p = settings.profile
        appendLine("=== USER DATA (today is $today, ${today.dayOfWeek.name.lowercase()}) ===")

        val profile = listOfNotNull(
            p.sex?.let { "sex: $it" },
            p.age?.let { "age: $it" },
            p.heightCm?.let { "height: ${f1(it)} cm" },
            p.activityLevel?.let { "activity: $it" },
            p.goalWeightKg?.let { "goal weight: ${f1(it)} kg" },
            p.calorieTarget?.let { "calorie target: $it kcal/day" },
            p.proteinTarget?.let { "protein target: $it g/day" },
            p.waterTargetMl?.let { "water target: $it ml/day" },
        )
        appendLine("Profile: " + profile.ifEmpty { listOf("not filled in") }.joinToString(", "))
        if (p.notes.isNotBlank()) appendLine("User notes: ${p.notes.trim()}")
        if (memories.isNotEmpty()) {
            appendLine("Things you remembered about the user:")
            memories.forEach { appendLine("- ${it.text}") }
        }

        if (weights.isEmpty()) {
            appendLine("Weight: no weigh-ins logged yet.")
        } else {
            val sorted = weights.sortedBy { it.date }
            val first = sorted.first()
            val latest = sorted.last()
            appendLine("Weight: latest ${f1(latest.kg)} kg on ${latest.date}; first logged ${f1(first.kg)} kg on ${first.date} (total change ${signed(latest.kg - first.kg)} kg, ${sorted.size} weigh-ins).")
            val last7 = Stats.averageBetween(sorted, today.minusDays(6), today)
            val prev7 = Stats.averageBetween(sorted, today.minusDays(13), today.minusDays(7))
            if (last7 != null) {
                append("7-day average: ${f1(last7)} kg")
                if (prev7 != null) append(" (previous 7 days: ${f1(prev7)} kg, change ${signed(last7 - prev7)} kg)")
                appendLine(".")
            }
            Stats.trendPerWeek(sorted.filter { it.date >= today.minusDays(29) })?.let {
                appendLine("30-day trend (linear fit): ${signed(it)} kg/week.")
            }
            p.heightCm?.let { h -> appendLine("Current BMI: ${f1(latest.kg / ((h / 100) * (h / 100)))}.") }
        }

        val weeks = Stats.weeklySummaries(weights, foods).take(8)
        if (weeks.isNotEmpty()) {
            appendLine()
            appendLine("Weekly averages (newest first, weeks start Monday):")
            weeks.forEach { w ->
                val parts = listOfNotNull(
                    w.avgWeight?.let { "weight ${f1(it)} kg (${w.weighIns} weigh-ins)" },
                    w.avgCalories?.let { "${it.toInt()} kcal" },
                    w.avgProtein?.let { "${it.toInt()} g protein" },
                    if (w.daysLogged > 0) "food logged ${w.daysLogged} days" else null,
                )
                appendLine("- week of ${w.weekStart}: ${parts.joinToString(", ")}")
            }
        }

        val days = Stats.dailyNutrition(foods).filter { it.date >= today.minusDays(13) && it.hasData }
        if (days.isNotEmpty()) {
            appendLine()
            appendLine("Daily nutrition totals, last 14 days (kcal / protein / carbs / fat / fiber in g):")
            days.forEach { d ->
                val n = d.total
                val pending = if (d.pendingCount > 0) " (+${d.pendingCount} items not yet calculated)" else ""
                appendLine("- ${d.date}: ${n.calories.toInt()} / ${n.protein.toInt()} / ${n.carbs.toInt()} / ${n.fat.toInt()} / ${n.fiber.toInt()}$pending")
            }
        }

        val todayFoods = foods.filter { it.date == today }
        appendLine()
        if (todayFoods.isEmpty()) {
            appendLine("Eaten today so far: nothing logged yet.")
        } else {
            appendLine("Eaten today so far:")
            todayFoods.forEach { f ->
                val n = f.nutrition?.let { " — ${it.calories.toInt()} kcal, ${it.protein.toInt()} g protein, ${it.carbs.toInt()} g carbs, ${it.fat.toInt()} g fat" } ?: " — not calculated"
                appendLine("- ${f.name}${if (f.amount.isNotBlank()) " (${f.amount})" else ""}$n")
            }
            val t = todayFoods.mapNotNull { it.nutrition }.fold(Nutrition.ZERO, Nutrition::plus)
            appendLine("Today's totals: ${t.calories.toInt()} kcal, ${t.protein.toInt()} g protein, ${t.carbs.toInt()} g carbs, ${t.fat.toInt()} g fat, ${t.fiber.toInt()} g fiber.")
            val left = listOfNotNull(
                p.calorieTarget?.let { "${it - t.calories.toInt()} kcal" },
                p.proteinTarget?.let { "${it - t.protein.toInt()} g protein" },
            )
            if (left.isNotEmpty()) appendLine("Remaining for today vs targets: ${left.joinToString(", ")} (negative = over).")
        }
        appendLine("Current local time: ${LocalTime.now().withSecond(0).withNano(0)}.")

        val waterByDay = water.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.ml } }
        val waterToday = waterByDay[today] ?: 0
        val recentWater = (1L..7L).mapNotNull { waterByDay[today.minusDays(it)] }
        appendLine()
        append("Water: $waterToday ml today (target ${p.effectiveWaterTargetMl} ml)")
        if (recentWater.isNotEmpty()) append("; previous 7 days average ${recentWater.average().toInt()} ml over ${recentWater.size} logged days")
        appendLine(".")
    }

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun signed(v: Double) = String.format(Locale.US, "%+.2f", v)

    companion object {
        private const val BATCH_SIZE = 40
        private const val MAX_HISTORY = 20

        private const val NUTRITION_PROMPT = """You are a precise nutrition database.
You receive a JSON array of foods a person ate, each with an id, a food description and an amount.
For every item, estimate the nutrition for the WHOLE amount given (not per 100 g), using typical values
(e.g. USDA / standard food composition tables) and typical recipes for prepared or regional dishes.
If the amount is vague (e.g. "1 plate", "a bowl", "some"), assume a typical portion and state the assumed
weight briefly in "assumption". Otherwise leave "assumption" empty.
Return exactly one result per input id."""

        private const val COACH_PROMPT = """You are a friendly, knowledgeable fitness and nutrition coach inside a
personal fitness-tracking app. The user logs their daily body weight, the food they eat and the water they drink.
A summary of their data is included below; use it to give specific, personalised answers that reference
their actual numbers. Daily weight fluctuates by 1-2 kg from water and food, so judge progress from
weekly averages and trends, not single days. Be practical and concise (short paragraphs or bullet lists,
simple markdown only). If data is missing, say what would help. You are not a doctor: for medical
concerns, recommend seeing a professional.

When asked what to eat: look at what they already ate today, the remaining calories/protein for today,
the time of day and their remembered preferences, and suggest 2-3 concrete meal or snack options with
approximate portions, calories and protein for each.

You respond in JSON:
- "reply": your message to the user (markdown allowed).
- "profile_updates": ONLY fields the user explicitly told you in their latest message (height, age, sex,
  activity level, goal weight) or explicitly asked you to set (e.g. "set my calorie target"). If they ask
  you to work out targets for them, compute sensible values and include them. Otherwise leave it empty.
  Convert units: height in cm, weights in kg, water in ml.
- "log_weight_kg": only if the user states what they weigh today (e.g. "I'm 82 kg this morning").
- "remember": new lasting facts worth remembering for future chats (diet, allergies, dislikes, training
  schedule, injuries, medical conditions, motivation), each a short sentence. Do not repeat facts already
  remembered or stored in the profile. Usually empty.
When you save something, briefly confirm it in "reply"."""

        private val coachSchema: JSONObject by lazy {
            fun t(type: String) = JSONObject().put("type", type)
            val profile = JSONObject()
                .put("height_cm", t("NUMBER"))
                .put("age", t("INTEGER"))
                .put("sex", t("STRING").put("enum", JSONArray(listOf("male", "female", "other"))))
                .put("activity_level", t("STRING").put("enum", JSONArray(listOf("sedentary", "light", "moderate", "very active"))))
                .put("goal_weight_kg", t("NUMBER"))
                .put("calorie_target", t("INTEGER"))
                .put("protein_target_g", t("INTEGER"))
                .put("water_target_ml", t("INTEGER"))
            JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties", JSONObject()
                        .put("reply", t("STRING"))
                        .put("profile_updates", t("OBJECT").put("properties", profile))
                        .put("log_weight_kg", t("NUMBER"))
                        .put("remember", t("ARRAY").put("items", t("STRING")))
                )
                .put("required", JSONArray(listOf("reply")))
                .put("propertyOrdering", JSONArray(listOf("reply", "profile_updates", "log_weight_kg", "remember")))
        }

        private val nutritionSchema: JSONObject by lazy {
            fun num() = JSONObject().put("type", "NUMBER")
            val item = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties", JSONObject()
                        .put("id", JSONObject().put("type", "INTEGER"))
                        .put("calories", num())
                        .put("protein_g", num())
                        .put("carbs_g", num())
                        .put("fat_g", num())
                        .put("fiber_g", num())
                        .put("sugar_g", num())
                        .put("sodium_mg", num())
                        .put("assumption", JSONObject().put("type", "STRING"))
                )
                .put("required", JSONArray(listOf("id", "calories", "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g", "sodium_mg")))
            JSONObject()
                .put("type", "OBJECT")
                .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", item)))
                .put("required", JSONArray(listOf("items")))
        }
    }
}
