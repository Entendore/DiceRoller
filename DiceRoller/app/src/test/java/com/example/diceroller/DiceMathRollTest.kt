package com.example.diceroller

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceMathRollTest {

    @Test fun `respects dice count across seeds`() {
        for (seed in 1L..5L) {
            val o = DiceMath.roll(ParsedRoll(7, 20), rng = Random(seed))
            assertEquals(7, o.allDice.size); assertEquals(7, o.keptDice.size)
        }
    }

    @Test fun `faces stay within bounds for many sizes and seeds`() {
        for (s in listOf(4, 6, 8, 10, 12, 20, 100)) {
            for (seed in 0L..9L) {
                val o = DiceMath.roll(ParsedRoll(30, s), rng = Random(seed))
                assertTrue("d$s out of bounds: ${o.allDice}", o.allDice.all { it in 1..s })
            }
        }
    }

    @Test fun `same seed reproduces the exact same roll`() {
        val a = DiceMath.roll(ParsedRoll(5, 6, 3), rng = Random(4242))
        val b = DiceMath.roll(ParsedRoll(5, 6, 3), rng = Random(4242))
        assertEquals(a, b)
    }

    @Test fun `different seeds produce different sequences`() {
        val a = DiceMath.roll(ParsedRoll(100, 6), rng = Random(1))
        val b = DiceMath.roll(ParsedRoll(100, 6), rng = Random(2))
        assertNotEquals(a.allDice, b.allDice)
    }

    @Test fun `modifier is applied exactly once to the total`() {
        val force6 = listOf(0f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(20, DiceMath.roll(ParsedRoll(3, 6, 2), force6).total)
        assertEquals(14, DiceMath.roll(ParsedRoll(3, 6, -4), force6).total)
        assertEquals(18, DiceMath.roll(ParsedRoll(3, 6, 0), force6).total)
    }

    @Test fun `total always equals kept sum plus modifier`() {
        for (seed in 0L..9L) {
            val o = DiceMath.roll(ParsedRoll(5, 6, -2), rng = Random(seed))
            assertEquals(o.keptDice.sum() - 2, o.total)
        }
    }

    @Test fun `forced natural 20 on d20 is a critical`() {
        val force20 = List(19) { 0f } + 1f
        val o = DiceMath.roll(ParsedRoll(1, 20), force20)
        assertTrue(o.isCritical); assertFalse(o.isFumble)
    }

    @Test fun `forced natural 1 on d20 is a fumble`() {
        val force1 = listOf(1f) + List(19) { 0f }
        val o = DiceMath.roll(ParsedRoll(1, 20), force1)
        assertTrue(o.isFumble); assertFalse(o.isCritical)
    }

    @Test fun `max face on non-d20 is not a critical`() {
        val force6 = listOf(0f, 0f, 0f, 0f, 0f, 1f)
        assertFalse(DiceMath.roll(ParsedRoll(1, 6), force6).isCritical)
    }

    @Test fun `kh keeps the N highest dice self-consistently`() {
        for (seed in 0L..19L) {
            val o = DiceMath.roll(ParsedRoll(5, 6, keepHighest = 3), rng = Random(seed))
            assertEquals(o.allDice.sortedDescending().take(3), o.keptDice)
        }
    }

    @Test fun `kl keeps the N lowest dice self-consistently`() {
        for (seed in 0L..19L) {
            val o = DiceMath.roll(ParsedRoll(5, 6, keepLowest = 2), rng = Random(seed))
            assertEquals(o.allDice.sorted().take(2), o.keptDice)
        }
    }

    @Test fun `without a keep rule every die is kept`() {
        val o = DiceMath.roll(ParsedRoll(4, 6), rng = Random(3))
        assertEquals(o.allDice, o.keptDice)
    }

    @Test fun `fair d6 distribution is roughly uniform`() {
        val counts = IntArray(6)
        val rng = Random(99)
        repeat(20000) { counts[DiceMath.weightedFace(List(6) { 1f }, rng) - 1]++ }
        for ((i, c) in counts.withIndex()) {
            assertTrue("face ${i + 1} rolled $c times (expected ~3333)", c in 2800..3900)
        }
    }

    @Test fun `fair d20 mean is near 10_5`() {
        val rng = Random(7)
        var sum = 0L
        repeat(20000) { sum += DiceMath.roll(ParsedRoll(1, 20), rng = rng).keptDice[0] }
        val mean = sum / 20000.0
        assertTrue("mean was $mean", mean in 10.1..10.9)
    }

    @Test fun `discarded removes exactly the kept occurrences`() {
        assertEquals(listOf(3), DiceMath.discardedOf(listOf(3, 5, 5), listOf(5, 5)))
        assertEquals(listOf(2), DiceMath.discardedOf(listOf(2, 2), listOf(2)))
        assertEquals(listOf(1, 2, 3), DiceMath.discardedOf(listOf(1, 2, 3), emptyList()))
        assertEquals(emptyList<Int>(), DiceMath.discardedOf(listOf(4, 4), listOf(4, 4)))
    }
}
