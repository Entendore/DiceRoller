package com.example.diceroller

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.lang.ref.WeakReference
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewModelLeakTest {

    @get:Rule val main = MainDispatcherRule()

    private fun gcUntil(condition: () -> Boolean, maxTries: Int = 20): Boolean {
        repeat(maxTries) {
            if (condition()) return true
            System.gc(); System.runFinalization(); Thread.sleep(20)
        }
        return condition()
    }

    private fun freshVm(store: ViewModelStore): DiceViewModel {
        val factory = viewModelFactory { initializer { DiceViewModel(FakeRepo()) } }
        return ViewModelProvider(store, factory)[DiceViewModel::class.java]
    }

    @Test fun `viewModel is collectible after its store is cleared`() = runTest(main.dispatcher) {
        val store = ViewModelStore()
        val vm = freshVm(store)
        advanceUntilIdle()

        val ref = WeakReference(vm)
        var strong: DiceViewModel? = vm
        strong = null
        store.clear()
        advanceUntilIdle()

        assertTrue("ViewModel was retained after store.clear()", gcUntil { ref.get() == null })
    }

    @Test fun `viewModel with emitted events is still collectible after clear`() =
        runTest(main.dispatcher) {
            val store = ViewModelStore()
            val vm = freshVm(store)
            advanceUntilIdle()

            vm.setTheme("Neon"); advanceUntilIdle()

            val ref = WeakReference(vm)
            var strong: DiceViewModel? = vm
            strong = null
            store.clear(); advanceUntilIdle()

            assertTrue(gcUntil { ref.get() == null })
        }
}
