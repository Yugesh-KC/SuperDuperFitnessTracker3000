package com.example.superduperfitnesstracker3000.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Settings live in SharedPreferences. API keys are kept in a separate file ("secrets")
 * which is excluded from cloud backup and device transfer (see res/xml backup rules).
 */
class SettingsRepository private constructor(context: Context) {

    companion object {
        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val keysJson = JSONArray(secrets.getString("api_keys", "[]"))
        val keys = List(keysJson.length()) { keysJson.getString(it) }
        val profile = UserProfile(
            heightCm = prefs.getDoubleOrNull("height_cm"),
            age = prefs.getIntOrNull("age"),
            sex = prefs.getString("sex", null),
            activityLevel = prefs.getString("activity", null),
            goalWeightKg = prefs.getDoubleOrNull("goal_weight"),
            calorieTarget = prefs.getIntOrNull("calorie_target"),
            proteinTarget = prefs.getIntOrNull("protein_target"),
            waterTargetMl = prefs.getIntOrNull("water_target"),
            notes = prefs.getString("notes", "") ?: "",
        )
        return AppSettings(
            apiKeys = keys,
            model = prefs.getString("model", null) ?: AppSettings.DEFAULT_MODEL,
            profile = profile,
            reminders = ReminderSettings(
                enabled = prefs.getBoolean("reminders_enabled", false),
                intervalMinutes = prefs.getInt("reminders_interval", 90),
                startMinute = prefs.getInt("reminders_start", 8 * 60),
                endMinute = prefs.getInt("reminders_end", 22 * 60),
            ),
        )
    }

    fun addApiKeys(newKeys: List<String>) {
        val keys = (current.apiKeys + newKeys.map { it.trim() }.filter { it.isNotEmpty() }).distinct()
        saveKeys(keys)
    }

    fun removeApiKey(key: String) = saveKeys(current.apiKeys - key)

    private fun saveKeys(keys: List<String>) {
        secrets.edit { putString("api_keys", JSONArray(keys).toString()) }
        _settings.value = current.copy(apiKeys = keys)
    }

    /** Index of the key to try first on the next request (round-robin). */
    var nextKeyIndex: Int
        get() = secrets.getInt("next_key_index", 0)
        set(value) = secrets.edit { putInt("next_key_index", value) }

    fun setModel(model: String) {
        val m = model.trim().removePrefix("models/").ifEmpty { AppSettings.DEFAULT_MODEL }
        prefs.edit { putString("model", m) }
        _settings.value = current.copy(model = m)
    }

    fun saveProfile(p: UserProfile) {
        prefs.edit {
            putOrRemove("height_cm", p.heightCm)
            putOrRemove("goal_weight", p.goalWeightKg)
            if (p.age != null) putInt("age", p.age) else remove("age")
            if (p.calorieTarget != null) putInt("calorie_target", p.calorieTarget) else remove("calorie_target")
            if (p.proteinTarget != null) putInt("protein_target", p.proteinTarget) else remove("protein_target")
            if (p.waterTargetMl != null) putInt("water_target", p.waterTargetMl) else remove("water_target")
            putString("sex", p.sex)
            putString("activity", p.activityLevel)
            putString("notes", p.notes)
        }
        _settings.value = current.copy(profile = p)
    }

    fun saveReminders(r: ReminderSettings) {
        prefs.edit {
            putBoolean("reminders_enabled", r.enabled)
            putInt("reminders_interval", r.intervalMinutes)
            putInt("reminders_start", r.startMinute)
            putInt("reminders_end", r.endMinute)
        }
        _settings.value = current.copy(reminders = r)
    }

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: Double?) {
        if (value != null) putString(key, value.toString()) else remove(key)
    }

    private fun android.content.SharedPreferences.getDoubleOrNull(key: String) =
        getString(key, null)?.toDoubleOrNull()

    private fun android.content.SharedPreferences.getIntOrNull(key: String) =
        if (contains(key)) getInt(key, 0) else null
}
