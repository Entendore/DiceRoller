package com.example.diceroller

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class DiceViewScreenshotTest {

    @get:Rule val paparazzi = Paparazzi(theme = "Theme.DiceRoller.Classic")

    private fun sizedDiceView(sides: Int, value: Int, loaded: Boolean = false): DiceView {
        val px = (paparazzi.context.resources.displayMetrics.density * 100).toInt()
        return DiceView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(px, px)
            this.sides = sides; this.value = value; this.loaded = loaded
            measure(
                View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    @Test fun d6AllSixFaces() {
        for (v in 1..6) paparazzi.validateScreenshot(sizedDiceView(6, v), "d6_face$v")
    }

    @Test fun polygonShapesRender() {
        for (s in listOf(4, 8, 12, 20)) paparazzi.validateScreenshot(sizedDiceView(s, s / 2), "d$s")
    }

    @Test fun loadedDieShowsRedDot() =
        paparazzi.validateScreenshot(sizedDiceView(6, 5, loaded = true), "d6_loaded")

    @Test fun historyItemRenders() {
        val entry = HistoryEntry(2, 6, RollKind.NOTATION, "2d6+3", listOf(4, 6), 3, 13)
        val view = paparazzi.layoutInflater
            .inflate(R.layout.item_roll_history, null)
            .apply { findViewById<TextView>(R.id.historyText).text = RollText.history(context, entry) }
        paparazzi.validateScreenshot(view, "history_item")
    }
}
