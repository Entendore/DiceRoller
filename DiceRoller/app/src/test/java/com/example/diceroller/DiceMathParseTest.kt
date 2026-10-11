package com.example.diceroller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiceMathParseTest {

    @Test fun `plain d20 parses with implicit count of one`() {
        val p = DiceMath.parse("d20")!!
        assertEquals(1, p.count); assertEquals(20, p.sides); assertEquals(0, p.modifier)
    }

    @Test fun `count sides and positive modifier parse`() {
        val p = DiceMath.parse("2d6+3")!!
        assertEquals(2, p.count); assertEquals(6, p.sides); assertEquals(3, p.modifier)
    }

    @Test fun `negative modifier parses`() {
        assertEquals(-2, DiceMath.parse("3d6-2")!!.modifier)
    }

    @Test fun `whitespace and uppercase are ignored`() {
        val p = DiceMath.parse("  2 D 6 + 3  ")!!
        assertEquals(2, p.count); assertEquals(6, p.sides); assertEquals(3, p.modifier)
    }

    @Test fun `keep highest parses`() {
        val p = DiceMath.parse("4d6kh3")!!
        assertEquals(4, p.count); assertEquals(3, p.keepHighest)
    }

    @Test fun `keep lowest with negative modifier parses`() {
        val p = DiceMath.parse("2d20kl1-2")!!
        assertEquals(1, p.keepLowest); assertEquals(-2, p.modifier)
    }

    @Test fun `percentile die parses`() { assertEquals(100, DiceMath.parse("1d100")!!.sides) }
    @Test fun `single sided die parses`() { assertEquals(1, DiceMath.parse("1d1")!!.sides) }

    @Test fun `zero count rejected`() { assertNull(DiceMath.parse("0d6")) }
    @Test fun `too many dice rejected`() { assertNull(DiceMath.parse("21d6")) }
    @Test fun `zero sides rejected`() { assertNull(DiceMath.parse("d0")) }
    @Test fun `over hundred sides rejected`() { assertNull(DiceMath.parse("d101")) }
    @Test fun `keep exceeding count rejected`() {
        assertNull(DiceMath.parse("5d6kh6")); assertNull(DiceMath.parse("2d6kl3"))
    }
    @Test fun `keep of zero rejected`() { assertNull(DiceMath.parse("3d6kh0")) }

    @Test fun `garbage inputs rejected`() {
        assertNull(DiceMath.parse("banana"))
        assertNull(DiceMath.parse("d"))
        assertNull(DiceMath.parse("2d6+3x"))
        assertNull(DiceMath.parse("-2d6"))
        assertNull(DiceMath.parse("4d6+3kh3"))
    }

    @Test fun `largest legal roll accepted`() {
        val p = DiceMath.parse("20d100+9999")!!
        assertEquals(20, p.count); assertEquals(100, p.sides); assertEquals(9999, p.modifier)
    }
}
