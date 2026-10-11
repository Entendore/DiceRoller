package com.example.diceroller

import android.content.Context
import android.os.Handler
import android.view.View
import androidx.lifecycle.ViewModel
import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticContextGuardTest {

    private val classes = listOf(
        DiceApp::class.java, MainActivity::class.java, DiceViewModel::class.java,
        DiceDataStore::class.java, DiceView::class.java, ShakeDetector::class.java,
        RollHistoryAdapter::class.java, RollText::class.java, DiceMath::class.java,
        CrashGuard::class.java
    )

    @Test fun `no static fields hold Context View Handler or ViewModel`() {
        val forbidden = listOf(
            Context::class.java, View::class.java, Handler::class.java, ViewModel::class.java
        )
        val offenders = classes.flatMap { c ->
            c.declaredFields.asSequence()
                .filter { Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                .filter { f -> forbidden.any { it.isAssignableFrom(f.type) } }
                .map { "${c.simpleName}.${it.name} : ${it.type.simpleName}" }
                .toList()
        }
        assertTrue(
            "Static references to Android UI/context types are leak traps:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test fun `DiceViewModel stores no Context in any field`() {
        val bad = DiceViewModel::class.java.declaredFields
            .filter { Context::class.java.isAssignableFrom(it.type) }
            .map { it.name }
        assertTrue("ViewModel must be Context-free, found: $bad", bad.isEmpty())
    }
}
