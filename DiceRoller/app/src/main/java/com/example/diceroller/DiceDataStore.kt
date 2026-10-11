package com.example.diceroller

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

interface DiceRepository {
    suspend fun load(): StoredState
    suspend fun saveTheme(name: String)
    suspend fun saveConfig(config: RollConfig)
    suspend fun saveWeights(weights: Map<Int, List<Float>>)
    suspend fun savePresets(presets: List<Preset>)
    suspend fun appendHistory(entry: HistoryEntry)
}

/**
 * Instance-scoped DataStore-backed repository.
 * - Injectable file + scope  -> real persistence is Robolectric-testable.
 * - Corruption handler       -> a broken file degrades to defaults, not a crash.
 * - Legacy migration         -> one-time import of the pre-DataStore "dice_prefs".
 * - Theme mirror             -> MainActivity must call setTheme() synchronously in
 *   onCreate(), before any coroutine can run; a tiny SharedPreferences mirror
 *   ("dice_theme") serves that read.
 */
class DiceDataStore(
    private val appContext: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    file: File = File(appContext.filesDir, "datastore/dice_store.preferences_pb"),
    legacyPrefsName: String = "dice_prefs"
) : DiceRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mirror by lazy {
        appContext.getSharedPreferences("dice_theme", Context.MODE_PRIVATE)
    }

    private val store = PreferenceDataStoreFactory.create(
        scope = scope,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        migrations = listOf(SharedPreferencesMigration(appContext, legacyPrefsName)),
        produceFile = { file }
    )

    override suspend fun load(): StoredState {
        val prefs = store.data.first()
        prefs[key]?.let { raw -> decode(raw)?.let { return it } }

        // First launch after upgrade: rebuild state from legacy keys, then persist.
        val migrated = StoredState(
            theme = prefs[stringPreferencesKey("theme")] ?: "Classic",
            presets = prefs[stringPreferencesKey("presets")]?.let(::legacyPresets) ?: emptyList(),
            weights = prefs[stringPreferencesKey("weights")]?.let(::legacyWeights) ?: emptyMap()
        )
        persist(migrated)
        return migrated
    }

    override suspend fun saveTheme(name: String) {
        update { it.copy(theme = name) }
        mirror.edit().putString(KEY_THEME_MIRROR, name).apply()
    }

    override suspend fun saveConfig(config: RollConfig) = update { it.copy(config = config) }
    override suspend fun saveWeights(weights: Map<Int, List<Float>>) = update { it.copy(weights = weights) }
    override suspend fun savePresets(presets: List<Preset>) = update { it.copy(presets = presets) }
    override suspend fun appendHistory(entry: HistoryEntry) = update { state ->
        state.copy(history = (listOf(entry) + state.history).take(MAX_HISTORY))
    }

    /** Synchronous mirror population — call once from Application.onCreate(). */
    fun attachMirror() {
        val stored = runBlocking { store.data.first() }
        val name = stored[key]?.let(::decode)?.theme
            ?: stored[stringPreferencesKey("theme")]
            ?: "Classic"
        mirror.edit().putString(KEY_THEME_MIRROR, name).apply()
    }

    // Read-modify-write is atomic within DataStore's edit{}.
    private suspend fun update(transform: (StoredState) -> StoredState) {
        store.edit { prefs ->
            val current = prefs[key]?.let(::decode) ?: StoredState()
            prefs[key] = json.encodeToString(transform(current))
        }
    }

    private suspend fun persist(state: StoredState) {
        store.edit { it[key] = json.encodeToString(state) }
    }

    private fun decode(raw: String): StoredState? =
        runCatching { json.decodeFromString<StoredState>(raw) }.getOrNull()

    // ---- legacy org.json formats from the pre-DataStore versions ----

    private fun legacyPresets(raw: String): List<Preset> = try {
        JSONArray(raw).let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Preset(o.getString("name"), o.getString("notation"))
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun legacyWeights(raw: String): Map<Int, List<Float>> = try {
        JSONObject(raw).let { obj ->
            obj.keys().asSequence().associate { k ->
                val arr = obj.getJSONArray(k)
                k.toInt() to (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            }
        }
    } catch (_: Exception) { emptyMap() }

    companion object {
        private const val MAX_HISTORY = 5
        private const val KEY_THEME_MIRROR = "theme"
        private val key = stringPreferencesKey("state_json")
    }
}
