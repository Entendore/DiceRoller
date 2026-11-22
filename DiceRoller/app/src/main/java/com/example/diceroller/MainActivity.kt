package com.example.diceroller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.diceroller.databinding.ActivityMainBinding
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val vm: DiceViewModel by viewModels { (application as DiceApp).vmFactory }
    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: RollHistoryAdapter
    private var containerKey: String = ""
    private var lastResultId = -1L

    private val themeNames = listOf("Classic", "Dark", "Neon")
    private val themeStyles = listOf(
        R.style.Theme_DiceRoller_Classic, R.style.Theme_DiceRoller_Dark, R.style.Theme_DiceRoller_Neon
    )
    private val diceImages = listOf(
        R.drawable.dice_1, R.drawable.dice_2, R.drawable.dice_3,
        R.drawable.dice_4, R.drawable.dice_5, R.drawable.dice_6
    )
    private val diceSides = (1..20).toList() + listOf(100)

    private val shakeDetector = ShakeDetector(SHAKE_THRESHOLD, SHAKE_COOLDOWN_MS)
    private var sensorManager: SensorManager? = null

    // Managed dialogs: dismissed in onDestroy so an open dialog can never pin
    // this Activity in memory (the leak suite tests exactly this).
    private val managedDialogs = mutableListOf<AlertDialog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme must be applied before super.onCreate; the synchronous mirror
        // (populated by DiceApp.repo.attachMirror()) serves this read.
        val saved = getSharedPreferences("dice_theme", Context.MODE_PRIVATE)
            .getString("theme", "Classic") ?: "Classic"
        setTheme(themeStyles[themeNames.indexOf(saved).coerceAtLeast(0)])
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupSpinners()
        setupButtons()
        historyAdapter = RollHistoryAdapter()
        binding.historyRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            adapter = historyAdapter
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        observeState()
        observeEvents()
    }

    // ---------- state → views (one direction, no logic here) ----------

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s -> render(s) }
            }
        }
    }

    private fun render(s: DiceViewModel.UiState) {
        binding.rollButton.isEnabled = !s.rolling
        binding.rollNotationButton.isEnabled = !s.rolling

        selectSafe(binding.diceTypeSpinner, diceSides.indexOf(s.sides))
        selectSafe(binding.diceCountSpinner, s.count - 1)
        selectSafe(binding.orderSpinner, s.orderMode)
        selectSafe(binding.modeSpinner, s.rollMode)
        selectSafe(binding.fairnessSpinner, s.fairnessMode)
        if (binding.imagesSwitch.isChecked != s.useImages) binding.imagesSwitch.isChecked = s.useImages

        renderDice(s)

        when {
            !s.ready -> Unit
            s.rolling -> binding.resultText.setText(R.string.rolling)
            s.result != null -> {
                val r = s.result!!
                binding.resultText.text = RollText.resultLine(this, r)
                binding.totalText.text = getString(R.string.total_label, r.total)
                binding.totalText.setTextColor(
                    when {
                        r.isCritical -> getColor(R.color.crit_green)
                        r.isFumble -> getColor(R.color.crit_red)
                        else -> themeColor(android.R.attr.textColorPrimary)
                    }
                )
                if (r.id != lastResultId) {
                    lastResultId = r.id
                    val scale = if (r.isCritical || r.isFumble) 1.35f else 1.2f
                    for (i in 0 until binding.diceContainer.childCount) {
                        bounce(binding.diceContainer.getChildAt(i), scale)
                    }
                    maybeRequestReview()
                }
            }
            else -> {
                binding.resultText.setText(R.string.idle_prompt)
                binding.totalText.text = ""
            }
        }
        historyAdapter.submitList(s.history)
        renderPresets(s)
    }

    private fun renderDice(s: DiceViewModel.UiState) {
        val key = s.faces.joinToString("|") { "${it.sides}:${it.isImage}" }
        if (key != containerKey) {
            containerKey = key
            binding.diceContainer.removeAllViews()
            val size = dp(when {
                s.faces.size <= 4 -> 110; s.faces.size <= 6 -> 90
                s.faces.size <= 9 -> 72; s.faces.size <= 14 -> 60; else -> 52
            })
            s.faces.forEach { face ->
                val params = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) }
                if (face.isImage) {
                    binding.diceContainer.addView(ImageView(this).apply {
                        layoutParams = params; setImageResource(diceImages[0])
                    })
                } else {
                    binding.diceContainer.addView(DiceView(this).apply {
                        layoutParams = params; sides = face.sides; loaded = face.loaded
                    })
                }
            }
        }
        for (i in 0 until binding.diceContainer.childCount) {
            when (val v = binding.diceContainer.getChildAt(i)) {
                is DiceView -> v.value = s.faces[i].value
                is ImageView -> v.setImageResource(diceImages[s.faces[i].value - 1])
            }
        }
    }

    private fun renderPresets(s: DiceViewModel.UiState) {
        binding.presetsHint.visibility = if (s.presets.isEmpty()) View.VISIBLE else View.GONE
        binding.presetsContainer.removeAllViews()
        s.presets.forEachIndexed { index, preset ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            val play = Button(this).apply {
                text = "⚡ ${preset.name}  (${preset.notation})"; isAllCaps = false
                setOnClickListener {
                    binding.notationInput.setText(preset.notation)
                    vm.rollNotation(preset.notation)
                }
            }
            val del = Button(this).apply {
                text = "✕"; isAllCaps = false
                setOnClickListener { confirmDelete(preset) }
            }
            row.addView(play, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(del)
            binding.presetsContainer.addView(row)
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { e ->
                    when (e) {
                        is DiceViewModel.Event.RecreateTheme -> recreate()
                        is DiceViewModel.Event.InvalidNotation ->
                            toast(getString(R.string.toast_invalid_notation))
                        is DiceViewModel.Event.PresetSaved ->
                            toast(getString(R.string.toast_preset_saved, e.name))
                        is DiceViewModel.Event.WeightsSaved ->
                            toast(getString(R.string.toast_weights_saved, e.sides))
                        is DiceViewModel.Event.WeightsReset ->
                            toast(getString(R.string.toast_weights_reset, e.sides))
                    }
                }
            }
        }
    }

    // ---------- one-time view wiring ----------

    private fun setupSpinners() {
        binding.diceTypeSpinner.adapter = adapter(diceSides.map { "d$it" })
        binding.diceCountSpinner.adapter =
            adapter((1..6).map { resources.getQuantityString(R.plurals.dice_count, it, it) })
        binding.orderSpinner.adapter = adapter(resources.getStringArray(R.array.order_options).toList())
        binding.modeSpinner.adapter = adapter(resources.getStringArray(R.array.mode_options).toList())
        binding.fairnessSpinner.adapter = adapter(resources.getStringArray(R.array.fairness_options).toList())
        binding.themeSpinner.adapter = adapter(themeNames.map { "Theme: $it" })

        binding.diceTypeSpinner.onPos { vm.setSides(diceSides[it]) }
        binding.diceCountSpinner.onPos { vm.setCount(it + 1) }
        binding.orderSpinner.onPos { vm.setOrderMode(it) }
        binding.modeSpinner.onPos { vm.setRollMode(it) }
        binding.fairnessSpinner.onPos { vm.setFairnessMode(it) }
        binding.themeSpinner.onPos { vm.setTheme(themeNames[it]) }
        binding.imagesSwitch.setOnCheckedChangeListener { _, checked -> vm.setUseImages(checked) }
    }

    private fun setupButtons() {
        binding.rollButton.setOnClickListener { vm.roll() }
        binding.rollNotationButton.setOnClickListener { vm.rollNotation(binding.notationInput.text.toString()) }
        binding.editWeightsButton.setOnClickListener { showWeightEditor() }
        binding.testFairnessButton.setOnClickListener { showFairnessTest() }
        binding.savePresetButton.setOnClickListener { showSavePresetDialog() }
    }

    // ---------- dialogs (all managed) ----------

    private fun AlertDialog.Builder.showManaged(): AlertDialog =
        create().also { dialog ->
            managedDialogs += dialog
            dialog.setOnDismissListener { managedDialogs.remove(dialog) }
            dialog.show()
        }

    private fun showWeightEditor() {
        val s = vm.state.value
        val current = vm.weightsFor(s.sides) ?: List(s.sides) { 1f }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        val bars = mutableListOf<SeekBar>()
        for (f in 1..s.sides) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            val valueText = TextView(this).apply {
                text = "×${current[f - 1].toInt()}"; width = dp(44); gravity = Gravity.END
            }
            row.addView(TextView(this).apply { text = getString(R.string.weights_face, f); width = dp(72) })
            val bar = SeekBar(this).apply {
                max = 10; progress = current[f - 1].toInt().coerceIn(0, 10)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { valueText.text = "×$p" }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(valueText)
            container.addView(row); bars.add(bar)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weights_title, s.sides))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(R.string.action_save) { _, _ ->
                vm.saveCustomWeights(s.sides, bars.map { it.progress.toFloat() })
            }
            .setNeutralButton(R.string.weights_reset) { _, _ -> vm.resetWeights(s.sides) }
            .setNegativeButton(R.string.action_cancel, null)
            .showManaged()
    }

    private fun showFairnessTest() {
        val s = vm.state.value
        val w = vm.weightsFor(s.sides) ?: List(s.sides) { 1f }
        val trials = 2000
        val counts = IntArray(s.sides)
        repeat(trials) { counts[DiceMath.weightedFace(w, Random.Default) - 1]++ }
        val maxCount = counts.max().coerceAtLeast(1)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.fairness_body, trials, s.sides, String.format("%.1f", 100.0 / s.sides))
            textSize = 14f
        })
        for (f in 1..s.sides) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, 0)
            }
            row.addView(TextView(this).apply { text = "$f"; width = dp(36) })
            val bar = View(this)
            row.addView(bar, LinearLayout.LayoutParams(
                (dp(150) * counts[f - 1] / maxCount).coerceAtLeast(dp(2)), dp(16)
            ).apply { marginEnd = dp(8) })
            bar.setBackgroundColor(themeColor(R.attr.colorPrimary))
            row.addView(TextView(this).apply { text = String.format("%.1f%%", counts[f - 1] * 100f / trials) })
            container.addView(row)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.fairness_title, s.sides))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(R.string.action_done, null)
            .showManaged()
    }

    private fun showSavePresetDialog() {
        val notation = binding.notationInput.text.toString().trim()
        if (DiceMath.parse(notation) == null) { toast(getString(R.string.preset_needs_notation)); return }
        val input = EditText(this).apply { hint = getString(R.string.save_preset_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_preset_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                vm.addPreset(input.text.toString().trim().ifEmpty { notation }, notation)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .showManaged()
    }

    private fun confirmDelete(preset: Preset) {
        val index = vm.state.value.presets.indexOf(preset)
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_preset_title)
            .setMessage(getString(R.string.delete_preset_message, preset.name))
            .setPositiveButton(R.string.action_delete) { _, _ -> vm.deletePreset(index) }
            .setNegativeButton(R.string.action_cancel, null)
            .showManaged()
    }

    // ---------- in-app review (10th roll, once per install) ----------

    private fun maybeRequestReview() {
        val meta = getSharedPreferences("dice_meta", Context.MODE_PRIVATE)
        val rolls = meta.getInt("rolls", 0) + 1
        meta.edit().putInt("rolls", rolls).apply()
        if (rolls != 10 || meta.getBoolean("reviewed", false)) return
        meta.edit().putBoolean("reviewed", true).apply()
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) manager.launchReviewFlow(this, task.result)
        }
    }

    // ---------- shake (platform glue; logic itself is unit-tested) ----------

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() { super.onPause(); sensorManager?.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        if (shakeDetector.detect(event.values[0], event.values[1], event.values[2], System.currentTimeMillis())) {
            vm.roll()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        // Detach open dialogs so their windows can't pin this Activity.
        managedDialogs.toList().forEach { it.dismiss() }
        managedDialogs.clear()
        super.onDestroy()
    }

    // ---------- helpers ----------

    private fun android.widget.Spinner.onPos(onSelected: (Int) -> Unit) {
        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = onSelected(pos)
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun selectSafe(spinner: android.widget.Spinner, pos: Int) {
        if (spinner.selectedItemPosition != pos) spinner.setSelection(pos, false)
    }

    private fun adapter(items: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun themeColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        return try { ta.getColor(0, android.graphics.Color.GRAY) } finally { ta.recycle() }
    }

    private fun bounce(view: View, scale: Float) {
        listOf("scaleX", "scaleY").forEach {
            android.animation.ObjectAnimator.ofFloat(view, it, 1f, scale, 1f).apply { duration = 250; start() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_COOLDOWN_MS = 800L
    }
}
