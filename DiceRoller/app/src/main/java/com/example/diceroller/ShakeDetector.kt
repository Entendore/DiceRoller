package com.example.diceroller

/**
 * Pure shake-detection logic (threshold + cooldown) so it can be unit-tested
 * on the JVM without hardware or sensor events.
 */
class ShakeDetector(
    private val threshold: Float = 12f,
    private val cooldownMs: Long = 800L
) {
    private var primed = false
    private var lastShakeTime = 0L
    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f

    /** Feed one accelerometer sample. Returns true exactly when a shake fires. */
    fun detect(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (!primed) {
            lastX = x; lastY = y; lastZ = z
            primed = true
            return false
        }
        val delta = kotlin.math.abs(x - lastX) +
                    kotlin.math.abs(y - lastY) +
                    kotlin.math.abs(z - lastZ)
        lastX = x; lastY = y; lastZ = z
        if (delta < threshold) return false
        if (nowMs - lastShakeTime < cooldownMs) return false
        lastShakeTime = nowMs
        return true
    }

    fun reset() {
        primed = false
        lastShakeTime = 0L
        lastX = 0f; lastY = 0f; lastZ = 0f
    }
}
