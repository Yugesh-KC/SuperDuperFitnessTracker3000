package com.example.superduperfitnesstracker3000.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.superduperfitnesstracker3000.ai.FitnessAi
import com.example.superduperfitnesstracker3000.ai.GeminiClient
import com.example.superduperfitnesstracker3000.data.AppSettings
import com.example.superduperfitnesstracker3000.data.ChatMessage
import com.example.superduperfitnesstracker3000.data.ChatRole
import com.example.superduperfitnesstracker3000.data.CoachMemory
import com.example.superduperfitnesstracker3000.data.FitnessDatabase
import com.example.superduperfitnesstracker3000.data.FoodEntry
import com.example.superduperfitnesstracker3000.data.ReminderSettings
import com.example.superduperfitnesstracker3000.data.SettingsRepository
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.data.WaterEntry
import com.example.superduperfitnesstracker3000.data.WeightEntry
import com.example.superduperfitnesstracker3000.reminders.WaterReminders
import com.example.superduperfitnesstracker3000.ui.components.fmt1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = FitnessDatabase.get(app)
    private val settingsRepo = SettingsRepository.get(app)
    private val gemini = GeminiClient(settingsRepo)
    private val ai = FitnessAi(gemini)

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    private val _weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    val weights = _weights.asStateFlow()

    private val _foods = MutableStateFlow<List<FoodEntry>>(emptyList())
    val foods = _foods.asStateFlow()

    private val _water = MutableStateFlow<List<WaterEntry>>(emptyList())
    val water = _water.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat = _chat.asStateFlow()

    private val _memories = MutableStateFlow<List<CoachMemory>>(emptyList())
    val memories = _memories.asStateFlow()

    private val _calculating = MutableStateFlow(false)
    val calculating = _calculating.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading = _chatLoading.asStateFlow()

    /** Results of "Test keys" in settings: key -> OK or the error message. */
    private val _keyTestResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val keyTestResults = _keyTestResults.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    /** One-off messages shown in a snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        reload()
    }

    /** Reloads everything from the database (called on start and whenever the app returns to the foreground). */
    fun reload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _weights.value = db.allWeights()
                _foods.value = db.allFoods()
                _water.value = db.allWater()
                _chat.value = db.allChat()
                _memories.value = db.allMemories()
            }
        }
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }

    // ---- Weight ----

    fun saveWeight(date: LocalDate, kg: Double) = io {
        db.upsertWeight(WeightEntry(date, kg))
        _weights.value = db.allWeights()
    }

    fun deleteWeight(date: LocalDate) = io {
        db.deleteWeight(date)
        _weights.value = db.allWeights()
    }

    // ---- Food ----

    fun addFood(date: LocalDate, name: String, amount: String) = io {
        db.insertFood(date, name.trim(), amount.trim())
        _foods.value = db.allFoods()
    }

    fun updateFood(entry: FoodEntry) = io {
        db.updateFood(entry)
        _foods.value = db.allFoods()
    }

    fun deleteFood(id: Long) = io {
        db.deleteFood(id)
        _foods.value = db.allFoods()
    }

    /** Sends every not-yet-calculated food item (from any day) to Gemini in batched requests. */
    fun calculatePending() {
        if (_calculating.value) return
        val pending = _foods.value.filter { !it.isCalculated }
        if (pending.isEmpty()) return
        _calculating.value = true
        io {
            try {
                val results = ai.analyzeFoods(pending)
                db.setFoodNutrition(results)
                _foods.value = db.allFoods()
                val missing = pending.size - results.size
                _messages.emit(
                    if (missing == 0) "Calculated ${results.size} item(s)"
                    else "Calculated ${results.size} item(s); $missing could not be estimated — try again"
                )
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Calculation failed")
            } finally {
                _calculating.value = false
            }
        }
    }

    // ---- Water ----

    fun addWater(date: LocalDate, ml: Int) = io {
        db.insertWater(date, ml)
        _water.value = db.allWater()
        if (date == LocalDate.now()) WaterReminders.dismiss(getApplication())
    }

    fun deleteWater(id: Long) = io {
        db.deleteWater(id)
        _water.value = db.allWater()
    }

    fun saveReminders(r: ReminderSettings) {
        settingsRepo.saveReminders(r)
        WaterReminders.schedule(getApplication())
    }

    fun testReminder() = io { WaterReminders.showReminder(getApplication(), test = true) }

    // ---- Coach chat ----

    fun sendChat(text: String) {
        if (text.isBlank() || _chatLoading.value) return
        _chatLoading.value = true
        io {
            db.insertChat(ChatRole.USER, text.trim())
            _chat.value = db.allChat()
            requestReply()
        }
    }

    /** Asks Gemini to answer the latest user message (also used for "Retry" after a failure). */
    fun retryChat() {
        if (_chatLoading.value) return
        _chatLoading.value = true
        io { requestReply() }
    }

    private suspend fun requestReply() {
        _chatLoading.value = true
        try {
            val reply = ai.chat(_chat.value, coachData())
            val saved = applyCoachUpdates(reply)
            db.insertChat(ChatRole.MODEL, reply.text.trim(), saved)
            _chat.value = db.allChat()
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Coach request failed")
        } finally {
            _chatLoading.value = false
        }
    }

    /** Saves whatever the coach extracted from the conversation. Returns a short summary, or null. */
    private fun applyCoachUpdates(reply: FitnessAi.CoachReply): String? {
        val saved = mutableListOf<String>()
        val u = reply.profileUpdates
        val old = settingsRepo.current.profile
        val new = old.copy(
            heightCm = u.heightCm ?: old.heightCm,
            age = u.age ?: old.age,
            sex = u.sex ?: old.sex,
            activityLevel = u.activityLevel ?: old.activityLevel,
            goalWeightKg = u.goalWeightKg ?: old.goalWeightKg,
            calorieTarget = u.calorieTarget ?: old.calorieTarget,
            proteinTarget = u.proteinTarget ?: old.proteinTarget,
            waterTargetMl = u.waterTargetMl ?: old.waterTargetMl,
        )
        if (new != old) {
            settingsRepo.saveProfile(new)
            if (new.heightCm != old.heightCm) saved += "height ${fmt1(new.heightCm!!)} cm"
            if (new.age != old.age) saved += "age ${new.age}"
            if (new.sex != old.sex) saved += "sex ${new.sex}"
            if (new.activityLevel != old.activityLevel) saved += "activity ${new.activityLevel}"
            if (new.goalWeightKg != old.goalWeightKg) saved += "goal ${fmt1(new.goalWeightKg!!)} kg"
            if (new.calorieTarget != old.calorieTarget) saved += "${new.calorieTarget} kcal/day"
            if (new.proteinTarget != old.proteinTarget) saved += "${new.proteinTarget} g protein/day"
            if (new.waterTargetMl != old.waterTargetMl) saved += "${new.waterTargetMl} ml water/day"
        }
        reply.logWeightKg?.let {
            db.upsertWeight(WeightEntry(LocalDate.now(), it))
            _weights.value = db.allWeights()
            saved += "today's weight ${fmt1(it)} kg"
        }
        val existing = _memories.value.map { it.text.lowercase() }.toSet()
        val newMemories = reply.remember.filter { it.lowercase() !in existing }
        if (newMemories.isNotEmpty()) {
            newMemories.forEach { db.insertMemory(it) }
            _memories.value = db.allMemories()
            saved += newMemories.map { "“$it”" }
        }
        return saved.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun coachData() = FitnessAi.CoachData(settings.value, _weights.value, _foods.value, _water.value, _memories.value)

    fun clearChat() = io {
        db.clearChat()
        _chat.value = db.allChat()
    }

    fun coachContextPreview(): String = ai.buildContext(coachData())

    fun deleteMemory(id: Long) = io {
        db.deleteMemory(id)
        _memories.value = db.allMemories()
    }

    fun addMemory(text: String) = io {
        if (text.isBlank()) return@io
        db.insertMemory(text.trim())
        _memories.value = db.allMemories()
    }

    // ---- Settings ----

    fun addApiKeys(raw: String) {
        // Allow pasting several keys at once, separated by newlines, commas or spaces.
        val keys = raw.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
        if (keys.isEmpty()) return
        settingsRepo.addApiKeys(keys)
        viewModelScope.launch { _messages.emit("Added ${keys.size} key(s)") }
    }

    fun removeApiKey(key: String) {
        settingsRepo.removeApiKey(key)
        _keyTestResults.value = _keyTestResults.value - key
    }

    fun testKeys() {
        val keys = settings.value.apiKeys
        _keyTestResults.value = emptyMap()
        io {
            keys.forEach { key ->
                _keyTestResults.value = _keyTestResults.value + (key to (gemini.testKey(key) ?: OK))
            }
        }
    }

    fun loadModels() = io {
        try {
            _availableModels.value = gemini.listModels()
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Could not load models")
        }
    }

    fun setModel(model: String) = settingsRepo.setModel(model)

    fun saveProfile(profile: UserProfile) {
        settingsRepo.saveProfile(profile)
        viewModelScope.launch { _messages.emit("Profile saved") }
    }

    companion object {
        const val OK = "OK"
    }
}
