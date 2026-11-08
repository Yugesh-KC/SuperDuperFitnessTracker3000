package com.example.superduperfitnesstracker3000.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Plain SQLite storage. All calls are blocking; call from Dispatchers.IO. Use [FitnessDatabase.get]. */
class FitnessDatabase private constructor(context: Context) : SQLiteOpenHelper(context, "fitness.db", null, 2) {

    companion object {
        @Volatile private var instance: FitnessDatabase? = null

        fun get(context: Context): FitnessDatabase =
            instance ?: synchronized(this) {
                instance ?: FitnessDatabase(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE weights (date TEXT PRIMARY KEY, kg REAL NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE foods (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                name TEXT NOT NULL,
                amount TEXT NOT NULL,
                calories REAL, protein REAL, carbs REAL, fat REAL,
                fiber REAL, sugar REAL, sodium REAL,
                note TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_foods_date ON foods(date)")
        db.execSQL(
            "CREATE TABLE chat (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
        onUpgrade(db, 1, 2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE chat ADD COLUMN saved_note TEXT")
            db.execSQL("CREATE TABLE water (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, ml INTEGER NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX idx_water_date ON water(date)")
            db.execSQL("CREATE TABLE memory (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at INTEGER NOT NULL)")
        }
    }

    // ---- Weights ----

    fun upsertWeight(entry: WeightEntry) {
        val values = ContentValues().apply {
            put("date", entry.date.toString())
            put("kg", entry.kg)
        }
        writableDatabase.insertWithOnConflict("weights", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWeight(date: LocalDate) {
        writableDatabase.delete("weights", "date = ?", arrayOf(date.toString()))
    }

    fun allWeights(): List<WeightEntry> =
        readableDatabase.rawQuery("SELECT date, kg FROM weights ORDER BY date", null).use { c ->
            buildList {
                while (c.moveToNext()) add(WeightEntry(LocalDate.parse(c.getString(0)), c.getDouble(1)))
            }
        }

    // ---- Foods ----

    fun insertFood(date: LocalDate, name: String, amount: String): Long {
        val values = ContentValues().apply {
            put("date", date.toString())
            put("name", name)
            put("amount", amount)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("foods", null, values)
    }

    fun updateFood(entry: FoodEntry) {
        val values = ContentValues().apply {
            put("date", entry.date.toString())
            put("name", entry.name)
            put("amount", entry.amount)
            putNutrition(entry.nutrition)
            put("note", entry.note)
        }
        writableDatabase.update("foods", values, "id = ?", arrayOf(entry.id.toString()))
    }

    fun setFoodNutrition(results: Map<Long, Pair<Nutrition, String?>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((id, result) in results) {
                val values = ContentValues().apply {
                    putNutrition(result.first)
                    put("note", result.second)
                }
                db.update("foods", values, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteFood(id: Long) {
        writableDatabase.delete("foods", "id = ?", arrayOf(id.toString()))
    }

    fun allFoods(): List<FoodEntry> =
        readableDatabase.rawQuery(
            "SELECT id, date, name, amount, calories, protein, carbs, fat, fiber, sugar, sodium, note, created_at FROM foods ORDER BY date, created_at",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val nutrition = if (c.isNull(4)) null else Nutrition(
                        calories = c.getDouble(4),
                        protein = c.getDoubleOrZero(5),
                        carbs = c.getDoubleOrZero(6),
                        fat = c.getDoubleOrZero(7),
                        fiber = c.getDoubleOrZero(8),
                        sugar = c.getDoubleOrZero(9),
                        sodiumMg = c.getDoubleOrZero(10),
                    )
                    add(
                        FoodEntry(
                            id = c.getLong(0),
                            date = LocalDate.parse(c.getString(1)),
                            name = c.getString(2),
                            amount = c.getString(3),
                            nutrition = nutrition,
                            note = if (c.isNull(11)) null else c.getString(11),
                            createdAt = c.getLong(12),
                        )
                    )
                }
            }
        }

    // ---- Chat ----

    fun insertChat(role: ChatRole, text: String, savedNote: String? = null) {
        val values = ContentValues().apply {
            put("role", role.name)
            put("text", text)
            put("created_at", System.currentTimeMillis())
            put("saved_note", savedNote)
        }
        writableDatabase.insert("chat", null, values)
    }

    fun allChat(): List<ChatMessage> =
        readableDatabase.rawQuery("SELECT id, role, text, created_at, saved_note FROM chat ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(ChatMessage(c.getLong(0), ChatRole.valueOf(c.getString(1)), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getString(4)))
                }
            }
        }

    fun clearChat() {
        writableDatabase.delete("chat", null, null)
    }

    // ---- Water ----

    fun insertWater(date: LocalDate, ml: Int) {
        val values = ContentValues().apply {
            put("date", date.toString())
            put("ml", ml)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("water", null, values)
    }

    fun deleteWater(id: Long) {
        writableDatabase.delete("water", "id = ?", arrayOf(id.toString()))
    }

    fun allWater(): List<WaterEntry> =
        readableDatabase.rawQuery("SELECT id, date, ml, created_at FROM water ORDER BY date, created_at", null).use { c ->
            buildList {
                while (c.moveToNext()) add(WaterEntry(c.getLong(0), LocalDate.parse(c.getString(1)), c.getInt(2), c.getLong(3)))
            }
        }

    fun waterTotal(date: LocalDate): Int =
        readableDatabase.rawQuery("SELECT COALESCE(SUM(ml), 0) FROM water WHERE date = ?", arrayOf(date.toString())).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // ---- Coach memory ----

    fun insertMemory(text: String) {
        val values = ContentValues().apply {
            put("text", text)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("memory", null, values)
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memory", "id = ?", arrayOf(id.toString()))
    }

    fun allMemories(): List<CoachMemory> =
        readableDatabase.rawQuery("SELECT id, text, created_at FROM memory ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) add(CoachMemory(c.getLong(0), c.getString(1), c.getLong(2)))
            }
        }

    private fun ContentValues.putNutrition(n: Nutrition?) {
        if (n == null) {
            listOf("calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium").forEach { putNull(it) }
        } else {
            put("calories", n.calories)
            put("protein", n.protein)
            put("carbs", n.carbs)
            put("fat", n.fat)
            put("fiber", n.fiber)
            put("sugar", n.sugar)
            put("sodium", n.sodiumMg)
        }
    }

    private fun Cursor.getDoubleOrZero(index: Int) = if (isNull(index)) 0.0 else getDouble(index)
}
