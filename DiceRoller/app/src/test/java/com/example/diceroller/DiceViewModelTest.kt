package com.example.diceroller

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiceViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private suspend fun vm(): Pair<DiceViewModel, FakeRepo> {
        val repo = FakeRepo()
        val vm = DiceViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        return vm to repo
    }

    @Test fun `roll produces dice within bounds`() = runTest(main.dispatcher) {
        val (vm, _) = vm()
        vm.setCount(4); advanceUntilIdle()
        vm.roll(); advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.rolling)
        assertEquals(4, s.faces.size)
        assertTrue(s.faces.all { it.value in 1..6 })
        assertEquals(s.faces.map { it.value }.sum(), s.result!!.total)
    }

    @Test fun `d1 always rolls one`() = runTest(main.dispatcher) {
        val (vm, _) = vm()
        vm.setSides(1); vm.setCount(1); advanceUntilIdle()
        repeat(5) { vm.roll(); advanceUntilIdle() }
        assertEquals(1, vm.state.value.result!!.total)
        assertTrue(vm.state.value.history.all { it.total == 1 })
    }

    @Test fun `loaded die is deterministic when only one face has weight`() =
        runTest(main.dispatcher) {
            val (vm, repo) = vm()
            vm.setSides(6); vm.setCount(3); advanceUntilIdle()
            vm.saveCustomWeights(6, listOf(0f, 0f, 0f, 0f, 0f, 1f)); advanceUntilIdle()
            assertEquals(3, vm.state.value.fairnessMode)
            vm.roll(); advanceUntilIdle()
            assertEquals(listOf(6, 6, 6), vm.state.value.result!!.values)
            assertTrue(repo.stored.weights.containsKey(6))
        }

    @Test fun `advantage keeps best and reports dropped`() = runTest(main.dispatcher) {
        val (vm, _) = vm()
        vm.setRollMode(1); vm.setCount(1); advanceUntilIdle()
        vm.roll(); advanceUntilIdle()
        val r = vm.state.value.result!!
        assertEquals(1, r.values.size); assertEquals(1, r.dropped.size)
        assertTrue(r.values[0] >= r.dropped[0])
        assertEquals(RollKind.ADVANTAGE, r.kind)
    }

    @Test fun `history caps at five with newest first`() = runTest(main.dispatcher) {
        val (vm, repo) = vm()
        vm.setCount(1); advanceUntilIdle()
        repeat(7) { vm.roll(); advanceUntilIdle() }
        assertEquals(5, vm.state.value.history.size)
        assertEquals(5, repo.stored.history.size)
    }

    @Test fun `invalid notation emits event and does not roll`() = runTest(main.dispatcher) {
        val (vm, _) = vm()
        vm.rollNotation("banana"); advanceUntilIdle()
        assertEquals(DiceViewModel.Event.InvalidNotation, vm.events.first())
        assertNull(vm.state.value.result)
    }

    @Test fun `preset save and delete persist to repository`() = runTest(main.dispatcher) {
        val (vm, repo) = vm()
        vm.addPreset("Fireball", "8d6"); advanceUntilIdle()
        assertEquals(listOf(Preset("Fireball", "8d6")), repo.stored.presets)
        vm.deletePreset(0); advanceUntilIdle()
        assertTrue(repo.stored.presets.isEmpty())
    }

    @Test fun `theme change persists and emits recreate event`() = runTest(main.dispatcher) {
        val (vm, repo) = vm()
        vm.setTheme("Neon"); advanceUntilIdle()
        assertEquals("Neon", repo.stored.theme)
        assertTrue(vm.events.first() is DiceViewModel.Event.RecreateTheme)
    }

    @Test fun `config changes persist`() = runTest(main.dispatcher) {
        val (vm, repo) = vm()
        vm.setSides(20); vm.setCount(5); vm.setOrderMode(2); advanceUntilIdle()
        assertEquals(20, repo.stored.config.sides)
        assertEquals(5, repo.stored.config.count)
        assertEquals(2, repo.stored.config.orderMode)
    }

    @Test fun `forced weights on d20 produce a critical`() = runTest(main.dispatcher) {
        val (vm, _) = vm()
        vm.setSides(20); vm.setCount(1); advanceUntilIdle()
        vm.saveCustomWeights(20, List(19) { 0f } + 1f); advanceUntilIdle()
        vm.roll(); advanceUntilIdle()
        assertTrue(vm.state.value.result!!.isCritical)
        assertFalse(vm.state.value.result!!.isFumble)
    }
}
