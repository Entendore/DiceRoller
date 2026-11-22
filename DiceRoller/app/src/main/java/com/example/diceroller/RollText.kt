package com.example.diceroller

import android.content.Context

/** Formats structured roll data into localized display strings. */
object RollText {

    fun label(c: Context, count: Int, sides: Int, kind: RollKind, notation: String): String =
        when (kind) {
            RollKind.ADVANTAGE -> c.getString(R.string.label_advantage, count, sides)
            RollKind.DISADVANTAGE -> c.getString(R.string.label_disadvantage, count, sides)
            RollKind.NOTATION -> notation
            RollKind.NORMAL -> c.getString(R.string.label_normal, count, sides)
        }

    private fun modSuffix(mod: Int) = when {
        mod > 0 -> " +$mod"
        mod < 0 -> " $mod"
        else -> ""
    }

    private fun droppedSuffix(c: Context, dropped: List<Int>) =
        if (dropped.isEmpty()) "" else c.getString(R.string.dropped_suffix, dropped.joinToString(", "))

    private fun loadTag(loaded: Boolean) = if (loaded) "⚖️ " else ""

    fun resultLine(c: Context, r: DiceViewModel.ResultData): String = when {
        r.isCritical -> c.getString(R.string.critical_hit)
        r.isFumble -> c.getString(R.string.critical_fumble)
        else -> loadTag(r.loaded) + label(c, r.count, r.sides, r.kind, r.notation) +
                " → [${r.values.joinToString(", ")}]" +
                modSuffix(r.modifier) + droppedSuffix(c, r.dropped)
    }

    fun history(c: Context, e: HistoryEntry): String =
        "🎲 " + loadTag(e.loaded) + label(c, e.count, e.sides, e.kind, e.notation) +
                " → [${e.values.joinToString(", ")}]" + modSuffix(e.modifier) +
                " = ${e.total}" + critTag(e)

    private fun critTag(e: HistoryEntry) = when {
        e.isCritical -> " 🎉"
        e.isFumble -> " 💀"
        else -> ""
    }
}
