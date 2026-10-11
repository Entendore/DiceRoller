package com.example.diceroller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class DiceViewModel(
    private val repo: DiceRepository,
    var rng: Random = Random.Default           // injected for deterministic tests
) : ViewModel() {

    // ---------- UI model ----------

    data class DiceFace(val sides: Int, val value: Int, val loaded: Boolean, val isImage: Boolean)

    data class ResultData(
        val id: Long,                          // identity token so the UI detects "new result"
        val count: Int, val sides: Int, val kind: RollKind, val notation: String,
        val values: List<Int>, val dropped: List<Int>, val modifier: Int, val total: Int,
        val isCritical: Boolean, val isFumble: Boolean, val loaded: Boolean
    )

    data class UiState(
        val ready: Boolean = false,
        val faces: List<DiceFace> = emptyList(),
        val rolling: Boolean = false,
        val result: ResultData? = null,
        val history: List<HistoryEntry> = emptyList(),
        val presets: List<Preset> = emptyList(),
        val weights: Map<Int, List<Float>> = emptyMap(),
        val sides: Int = 6, val count: Int = 2,
        val orderMode: Int = 0, val rollMode: Int = 0,
        val fairnessMode: Int = 0, val useImages: Boolean = false,
        val theme: String = "Classic"
    )

    sealed interface Event {
        data object RecreateTheme : Event
        data object InvalidNotation : Event
        data class PresetSaved(val name: String) : Event
        data class WeightsSaved(val sides: Int) : Event
        data class WeightsReset(val sides: Int) : Event
    }

    // ---------- state ----------

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rollJob: Job? = null
    private var resultSeq = 0L

    init {
        viewModelScope.launch {
            // Corrupt JSON degrades to defaults instead of crash-looping.
            val s = runCatching { repo.load() }.getOrElse { StoredState() }
            _state.update {
                UiState(
                    ready = true,
                    theme = s.theme,
                    weights = s.weights,
                    presets = s.presets,
                    history = s.history,
                    sides = s.config.sides, count = s.config.count,
                    orderMode = s.config.orderMode, rollMode = s.config.rollMode,
                    fairnessMode = s.config.fairnessMode, useImages = s.config.useImages,
                    faces = defaultFaces(s.config)
                )
            }
        }
    }

    private fun defaultFaces(c: RollConfig) = List(c.count) {
        DiceFace(c.sides, 1, isLoadedFor(c.fairnessMode, c.sides, mapOf()), c.useImages && c.sides == 6)
    }

    // ---------- configuration (each setter persists) ----------

    fun setSides(v: Int) = updateConfig { it.copy(sides = v.coerceIn(1, 100)) }
    fun setCount(v: Int) = updateConfig { it.copy(count = v.coerceIn(1, 6)) }
    fun setOrderMode(v: Int) = updateConfig { it.copy(orderMode = v.coerceIn(0, 2)) }
    fun setRollMode(v: Int) = updateConfig { it.copy(rollMode = v.coerceIn(0, 2)) }
    fun setFairnessMode(v: Int) = updateConfig { it.copy(fairnessMode = v.coerceIn(0, 3)) }
    fun setUseImages(v: Boolean) = updateConfig { it.copy(useImages = v) }

    // Side effects live OUTSIDE update{}: MutableStateFlow.update retries its
    // lambda under contention, so launching writes inside it can duplicate saves.
    private fun updateConfig(transform: (RollConfig) -> RollConfig) {
        if (rollJob?.isActive == true) return        // don't reconfigure mid-roll
        val cur = _state.value
        val cfg = transform(
            RollConfig(cur.sides, cur.count, cur.orderMode, cur.rollMode, cur.fairnessMode, cur.useImages)
        )
        viewModelScope.launch { repo.saveConfig(cfg) }
        _state.update { s -> s.copy(
            sides = cfg.sides, count = cfg.count, orderMode = cfg.orderMode,
            rollMode = cfg.rollMode, fairnessMode = cfg.fairnessMode, useImages = cfg.useImages,
            faces = List(cfg.count) { DiceFace(cfg.sides, 1, isLoaded(cfg.sides), cfg.useImages && cfg.sides == 6) },
            result = null
        ) }
    }

    fun setTheme(name: String) {
        if (name == _state.value.theme) return
        _state.update { it.copy(theme = name) }
        viewModelScope.launch { repo.saveTheme(name) }
        viewModelScope.launch { _events.send(Event.RecreateTheme) }
    }

    // ---------- weights ----------

    fun weightsFor(sides: Int): List<Float>? = when (_state.value.fairnessMode) {
        1 -> DiceMath.rampWeights(sides, favorHigh = true)
        2 -> DiceMath.rampWeights(sides, favorHigh = false)
        3 -> _state.value.weights[sides]
        else -> null
    }

    fun isLoaded(sides: Int): Boolean {
        val w = weightsFor(sides) ?: return false
        return w.any { it != w[0] }
    }

    private fun isLoadedFor(mode: Int, sides: Int, custom: Map<Int, List<Float>>): Boolean =
        when (mode) {
            1, 2 -> true
            3 -> custom[sides]?.let { w -> w.any { it != w[0] } } ?: false
            else -> false
        }

    fun saveCustomWeights(sides: Int, weights: List<Float>) {
        val clean = weights.map { it.coerceIn(0f, 10f) }
        if (clean.sum() <= 0f) { resetWeights(sides); return }
        val merged = _state.value.weights + (sides to clean)
        viewModelScope.launch { repo.saveWeights(merged) }
        _state.update { it.copy(weights = merged) }
        updateConfig { it.copy(fairnessMode = 3) }
        viewModelScope.launch { _events.send(Event.WeightsSaved(sides)) }
    }

    fun resetWeights(sides: Int) {
        val merged = _state.value.weights - sides
        viewModelScope.launch { repo.saveWeights(merged) }
        _state.update { it.copy(weights = merged) }
        updateConfig { it.copy(fairnessMode = 0) }
        viewModelScope.launch { _events.send(Event.WeightsReset(sides)) }
    }

    // ---------- rolling ----------

    fun roll() {
        val s = _state.value
        if (s.rolling || !s.ready) return
        val parsed = when (s.rollMode) {
            1 -> ParsedRoll(s.count * 2, s.sides, 0, keepHighest = s.count)   // advantage
            2 -> ParsedRoll(s.count * 2, s.sides, 0, keepLowest = s.count)    // disadvantage
            else -> ParsedRoll(s.count, s.sides)
        }
        val w = weightsFor(s.sides)
        animate(DiceMath.roll(parsed, w, rng), RollKind.NORMAL, "")
    }

    fun rollNotation(input: String) {
        val s = _state.value
        if (s.rolling || !s.ready) return
        val parsed = DiceMath.parse(input)
        if (parsed == null) {
            viewModelScope.launch { _events.send(Event.InvalidNotation) }
            return
        }
        val w = weightsFor(parsed.sides)
        animate(DiceMath.roll(parsed, w, rng), RollKind.NOTATION, input.replace(" ", "").lowercase())
    }

    private fun animate(outcome: RollOutcome, kind: RollKind, notation: String) {
        rollJob?.cancel()
        val sides = outcome.sides
        rollJob = viewModelScope.launch {
            _state.update { it.copy(rolling = true, result = null) }
            ensureFaces(outcome.keptDice.size, sides)
            repeat(TICKS) { i ->
                val w = weightsFor(sides)                 // bias even the tumble
                val tick = List(_state.value.faces.size) {
                    if (w != null && w.size == sides) DiceMath.weightedFace(w, rng)
                    else rng.nextInt(1, sides + 1)
                }
                _state.update { st ->
                    st.copy(faces = st.faces.mapIndexed { j, f -> f.copy(value = tick[j]) })
                }
                delay(50L + (i + 1) * 12L)                // virtualized in tests
            }
            finish(outcome, kind, notation)
        }
    }

    private suspend fun finish(outcome: RollOutcome, kind: RollKind, notation: String) {
        val s = _state.value
        val ordered = when (s.orderMode) {
            1 -> outcome.keptDice.sorted()
            2 -> outcome.keptDice.sortedDescending()
            else -> outcome.keptDice
        }
        val result = ResultData(
            id = ++resultSeq,
            count = outcome.keptDice.size, sides = outcome.sides, kind = kind, notation = notation,
            values = ordered, dropped = DiceMath.discardedOf(outcome.allDice, outcome.keptDice),
            modifier = outcome.modifier, total = outcome.total,
            isCritical = outcome.isCritical, isFumble = outcome.isFumble,
            loaded = isLoaded(outcome.sides)
        )
        val entry = HistoryEntry(
            count = result.count, sides = result.sides, kind = kind, notation = notation,
            values = ordered, modifier = outcome.modifier, total = outcome.total,
            isCritical = outcome.isCritical, isFumble = outcome.isFumble, loaded = result.loaded
        )
        repo.appendHistory(entry)   // side effect outside update{}
        _state.update {
            it.copy(
                rolling = false, result = result,
                faces = it.faces.mapIndexed { i, f -> f.copy(value = ordered[i]) },
                history = listOf(entry) + it.history.take(MAX_HISTORY - 1)
            )
        }
    }

    private fun ensureFaces(n: Int, sides: Int) {
        val s = _state.value
        if (s.faces.size == n && s.faces.firstOrNull()?.sides == sides) return
        val loaded = isLoaded(sides)
        val isImage = s.useImages && sides == 6
        _state.update {
            it.copy(faces = List(n) { DiceFace(sides, 1, loaded, isImage) })
        }
    }

    // ---------- presets ----------

    fun addPreset(name: String, notation: String) {
        val list = listOf(Preset(name, notation)) + _state.value.presets
        viewModelScope.launch { repo.savePresets(list) }
        _state.update { it.copy(presets = list) }
        viewModelScope.launch { _events.send(Event.PresetSaved(name)) }
    }

    fun deletePreset(index: Int) {
        val list = _state.value.presets.toMutableList().apply { if (index in indices) removeAt(index) }
        viewModelScope.launch { repo.savePresets(list) }
        _state.update { it.copy(presets = list) }
    }

    companion object {
        const val MAX_HISTORY = 5
        private const val TICKS = 14
    }
}
