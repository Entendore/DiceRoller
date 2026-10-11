package com.example.diceroller

import kotlinx.serialization.Serializable

@Serializable
enum class RollKind { NORMAL, ADVANTAGE, DISADVANTAGE, NOTATION }

@Serializable
data class RollConfig(
    val sides: Int = 6,
    val count: Int = 2,
    val orderMode: Int = 0,      // 0 as-rolled, 1 ascending, 2 descending
    val rollMode: Int = 0,       // 0 normal, 1 advantage, 2 disadvantage
    val fairnessMode: Int = 0,   // 0 fair, 1 loaded-high, 2 cursed-low, 3 custom
    val useImages: Boolean = false
)

@Serializable
data class Preset(val name: String, val notation: String)

@Serializable
data class HistoryEntry(
    val count: Int,
    val sides: Int,
    val kind: RollKind,
    val notation: String,
    val values: List<Int>,
    val modifier: Int,
    val total: Int,
    val isCritical: Boolean = false,
    val isFumble: Boolean = false,
    val loaded: Boolean = false
)

@Serializable
data class StoredState(
    val theme: String = "Classic",
    val config: RollConfig = RollConfig(),
    val weights: Map<Int, List<Float>> = emptyMap(),   // sides -> per-face weights
    val presets: List<Preset> = emptyList(),
    val history: List<HistoryEntry> = emptyList()      // newest first, capped at 5
)
