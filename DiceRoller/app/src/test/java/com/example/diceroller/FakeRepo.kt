package com.example.diceroller

/** Shared in-memory repository for ViewModel/JVM tests. */
class FakeRepo : DiceRepository {
    var stored = StoredState()
    override suspend fun load() = stored
    override suspend fun saveTheme(name: String) { stored = stored.copy(theme = name) }
    override suspend fun saveConfig(c: RollConfig) { stored = stored.copy(config = c) }
    override suspend fun saveWeights(w: Map<Int, List<Float>>) { stored = stored.copy(weights = w) }
    override suspend fun savePresets(p: List<Preset>) { stored = stored.copy(presets = p) }
    override suspend fun appendHistory(e: HistoryEntry) {
        stored = stored.copy(history = (listOf(e) + stored.history).take(5))
    }
}
