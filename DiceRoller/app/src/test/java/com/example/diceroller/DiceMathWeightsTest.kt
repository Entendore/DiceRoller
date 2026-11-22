package com.example.diceroller

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceMathWeightsTest {

    @Test fun `ramp for loaded d6 matches documented weights`() {
        val expected = listOf(1f, 2.2f, 3.4f, 4.6f, 5.8f, 7f)
        DiceMath.rampWeights(6, favorHigh = true).forEachIndexed { i, w ->
            assertEquals(expected[i], w, 1e-4f)
        }
    }

    @Test fun `ramp loaded low is the mirror of loaded high`() {
        val high = DiceMath.rampWeights(6, true)
        val low = DiceMath.rampWeights(6, false)
        high.reversed().forEachIndexed { i, e -> assertEquals(e, low[i], 1e-4f) }
    }

    @Test fun `ramp for d1 is a single unit weight`() =
        assertEquals(listOf(1f), DiceMath.rampWeights(1, true))

    @Test fun `ramp increases toward the favored end only`() {
        val up = DiceMath.rampWeights(20, true)
        val down = DiceMath.rampWeights(20, false)
        assertTrue(up.zipWithNext().all { (a, b) -> b > a })
        assertTrue(down.zipWithNext().all { (a, b) -> b < a })
    }

    @Test fun `zero-weight faces can never be rolled`() {
        val w = listOf(0f, 1f, 0f, 1f, 0f, 1f)
        val rng = Random(5)
        repeat(5000) {
            val f = DiceMath.weightedFace(w, rng)
            assertTrue("rolled impossible face $f", f == 2 || f == 4 || f == 6)
        }
    }

    @Test fun `single allowed face is certain`() {
        val w = listOf(0f, 0f, 0f, 0f, 0f, 1f)
        repeat(100) { s -> assertEquals(6, DiceMath.weightedFace(w, Random(s.toLong()))) }
    }

    @Test fun `all-zero weights fall back to a fair die`() {
        val rng = Random(8)
        val seen = mutableSetOf<Int>()
        repeat(1000) { seen += DiceMath.weightedFace(List(6) { 0f }, rng) }
        assertTrue("expected variety, saw $seen", seen.size > 1)
    }

    @Test fun `weighted d6 matches its stated probabilities`() {
        val w = listOf(1f, 1f, 1f, 1f, 1f, 7f)   // P(6)=7/24≈29.2%, P(1)=1/24≈4.2%
        val rng = Random(123)
        val counts = IntArray(6)
        repeat(20000) { counts[DiceMath.weightedFace(w, rng) - 1]++ }
        assertTrue("face 6: ${counts[5]}", counts[5] in 5300..6400)
        assertTrue("face 1: ${counts[0]}", counts[0] in 650..1050)
    }

    @Test fun `roll falls back to fair when weight size mismatches the die`() {
        val o = DiceMath.roll(ParsedRoll(20, 6), listOf(1f, 2f), Random(3))
        assertTrue(o.allDice.all { it in 1..6 })
    }

    @Test fun `weighted roll respects forced face and count`() {
        val force4 = listOf(0f, 0f, 0f, 1f, 0f, 0f)
        val o = DiceMath.roll(ParsedRoll(50, 6), force4, Random(2))
        assertEquals(List(50) { 4 }, o.allDice)
        assertEquals(200, o.total)
    }
}
