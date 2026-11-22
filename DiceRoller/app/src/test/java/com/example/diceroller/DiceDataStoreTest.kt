package com.example.diceroller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiceDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun TestScope.newRepo(file: File? = null): DiceDataStore = DiceDataStore(
        context,
        scope = CoroutineScope(backgroundScope.coroutineContext),
        file = file ?: File(context.filesDir, "test_${UUID.randomUUID()}.preferences_pb")
    )

    @Test fun `first launch returns defaults`() = runTest {
        val s = newRepo().load(); advanceUntilIdle()
        assertEquals("Classic", s.theme)
        assertTrue(s.presets.isEmpty())
    }

    @Test fun `state round-trips through save and load`() = runTest {
        val repo = newRepo()
        repo.load(); advanceUntilIdle()
        repo.saveTheme("Neon")
        repo.saveConfig(RollConfig(sides = 20, count = 4, fairnessMode = 1))
        repo.saveWeights(mapOf(6 to listOf(1f, 2f, 3f, 4f, 5f, 6f)))
        repo.savePresets(listOf(Preset("Fireball", "8d6")))
        advanceUntilIdle()

        val s = repo.load(); advanceUntilIdle()
        assertEquals("Neon", s.theme)
        assertEquals(20, s.config.sides)
        assertEquals(4, s.config.count)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f), s.weights[6])
        assertEquals(listOf(Preset("Fireball", "8d6")), s.presets)
    }

    @Test fun `history is capped at five newest first`() = runTest {
        val repo = newRepo()
        repo.load(); advanceUntilIdle()
        repeat(8) { i ->
            repo.appendHistory(HistoryEntry(1, 6, RollKind.NORMAL, "d6", listOf(i + 1), 0, i + 1))
        }
        advanceUntilIdle()
        val s = repo.load(); advanceUntilIdle()
        assertEquals(5, s.history.size)
        assertEquals(8, s.history.first().total)
        assertEquals(4, s.history.last().total)
    }

    @Test fun `corrupt state file falls back to defaults`() = runTest {
        // Write garbage BEFORE creating the repo so the fresh instance hits the
        // corruption handler (DataStore caches state in memory per instance).
        val file = File(context.filesDir, "corrupt_${UUID.randomUUID()}.preferences_pb")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 0, -1))

        val s = newRepo(file).load(); advanceUntilIdle()
        assertEquals("Classic", s.theme)
        assertTrue(s.presets.isEmpty())
    }

    @Test fun `legacy shared preferences are migrated`() = runTest {
        context.getSharedPreferences("dice_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("theme", "Neon")
            .putString("presets", """[{"name":"Fireball","notation":"8d6"}]""")
            .putString("weights", """{"6":[1.0,2.2,3.4,4.6,5.8,7.0]}""")
            .apply()

        val s = newRepo().load(); advanceUntilIdle()
        assertEquals("Neon", s.theme)
        assertEquals(listOf(Preset("Fireball", "8d6")), s.presets)
        assertEquals(7f, s.weights.getValue(6)[5])

        // Second load uses the persisted JSON state — idempotent.
        val again = newRepo().load(); advanceUntilIdle()
        assertEquals("Neon", again.theme)
    }
}
