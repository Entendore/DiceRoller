package com.example.diceroller

import kotlin.random.Random

data class ParsedRoll(
    val count: Int,
    val sides: Int,
    val modifier: Int = 0,
    val keepHighest: Int? = null,
    val keepLowest: Int? = null
)

data class RollOutcome(
    val sides: Int,
    val allDice: List<Int>,
    val keptDice: List<Int>,
    val modifier: Int,
    val total: Int,
    val isCritical: Boolean,
    val isFumble: Boolean
)

object DiceMath {

    // Matches: [N]dS [khN | klN] [+M | -M]   e.g. "2d6+3", "4d6kh3", "d100"
    private val pattern = Regex("""^(\d{0,2})d(\d{1,3})(?:kh(\d{1,2})|kl(\d{1,2}))?([+-]\d{1,4})?$""")

    fun parse(input: String): ParsedRoll? {
        val m = pattern.find(input.replace(" ", "").lowercase()) ?: return null
        val count = m.groupValues[1].ifEmpty { "1" }.toInt()
        val sides = m.groupValues[2].toInt()
        val kh = m.groupValues[3].toIntOrNull()
        val kl = m.groupValues[4].toIntOrNull()
        val mod = m.groupValues[5].toIntOrNull() ?: 0

        if (count !in 1..20 || sides !in 1..100) return null
        if (kh != null && kh !in 1..count) return null
        if (kl != null && kl !in 1..count) return null
        return ParsedRoll(count, sides, mod, kh, kl)
    }

    // rng is injectable so tests are deterministic; default keeps app behavior.
    fun roll(p: ParsedRoll, weights: List<Float>? = null, rng: Random = Random.Default): RollOutcome {
        fun nextFace(): Int =
            if (weights != null && weights.size == p.sides) weightedFace(weights, rng)
            else rng.nextInt(1, p.sides + 1)

        val all = List(p.count) { nextFace() }
        val kept = when {
            p.keepHighest != null -> all.sortedDescending().take(p.keepHighest)
            p.keepLowest != null -> all.sorted().take(p.keepLowest)
            else -> all
        }
        val isD20 = p.sides == 20
        return RollOutcome(
            sides = p.sides,
            allDice = all,
            keptDice = kept,
            modifier = p.modifier,
            total = kept.sum() + p.modifier,
            isCritical = isD20 && kept.contains(20),
            isFumble = isD20 && kept.contains(1)
        )
    }

    /** Roulette-wheel selection: face i is picked with probability weights[i] / sum. */
    fun weightedFace(weights: List<Float>, rng: Random = Random.Default): Int {
        var total = 0f
        weights.forEach { total += it }
        if (total <= 0f) return rng.nextInt(1, weights.size + 1)   // degenerate guard
        var r = rng.nextFloat() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r < 0f) return i + 1
        }
        return weights.size
    }

    /**
     * Linear ramp: the favored end gets 7x the weight of the other end.
     * d6 loaded high -> [1.0, 2.2, 3.4, 4.6, 5.8, 7.0] -> P(6) ~= 29%, P(1) ~= 4%.
     */
    fun rampWeights(sides: Int, favorHigh: Boolean): List<Float> {
        if (sides <= 1) return listOf(1f)
        return (1..sides).map { face ->
            val t = (face - 1).toDouble() / (sides - 1)
            val oriented = if (favorHigh) t else 1.0 - t
            (1.0 + 6.0 * oriented).toFloat()
        }
    }

    /** Dice that were thrown but not kept (advantage/disadvantage). */
    fun discardedOf(all: List<Int>, kept: List<Int>): List<Int> {
        val remaining = all.toMutableList()
        kept.forEach { remaining.remove(it) }   // removes one occurrence per kept die
        return remaining
    }
}
