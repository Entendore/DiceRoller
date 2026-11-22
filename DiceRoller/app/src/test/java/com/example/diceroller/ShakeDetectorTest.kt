package com.example.diceroller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    private val d = ShakeDetector(threshold = 12f, cooldownMs = 800)

    @Test fun `first sample only primes the detector`() {
        assertFalse(d.detect(0f, 0f, 0f, 0))
        assertFalse(d.detect(0f, 0f, 0f, 100))
    }

    @Test fun `small movement does not trigger`() {
        d.detect(0f, 0f, 0f, 0)
        assertFalse(d.detect(1f, 1f, 1f, 100))
    }

    @Test fun `big jolt triggers`() {
        d.detect(0f, 0f, 0f, 0)
        assertTrue(d.detect(0f, 0f, 20f, 100))
    }

    @Test fun `delta exactly at threshold triggers`() {
        d.detect(0f, 0f, 0f, 0)
        assertTrue(d.detect(12f, 0f, 0f, 100))
    }

    @Test fun `delta just under threshold does not trigger`() {
        d.detect(0f, 0f, 0f, 0)
        assertFalse(d.detect(11.9f, 0f, 0f, 100))
    }

    @Test fun `second shake within cooldown is ignored`() {
        d.detect(0f, 0f, 0f, 0)
        assertTrue(d.detect(20f, 0f, 0f, 100))
        assertFalse(d.detect(40f, 0f, 0f, 200))
    }

    @Test fun `shake after cooldown fires again`() {
        d.detect(0f, 0f, 0f, 0)
        assertTrue(d.detect(20f, 0f, 0f, 100))
        assertFalse(d.detect(40f, 0f, 0f, 500))
        assertTrue(d.detect(60f, 0f, 0f, 1000))
    }

    @Test fun `reset re-arms the detector`() {
        d.detect(0f, 0f, 0f, 0)
        assertTrue(d.detect(20f, 0f, 0f, 100))
        d.reset()
        assertFalse(d.detect(0f, 0f, 0f, 900))
        assertTrue(d.detect(30f, 0f, 0f, 950))
    }
}
