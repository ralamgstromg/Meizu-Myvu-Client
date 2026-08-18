package com.myvu.client.plugin.tasker.event

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.myvu.client.R
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants
import com.myvu.client.plugin.tasker.TaskerEvent

class TaskerEventActivity : AppCompatActivity() {

    private lateinit var actvEventType: MaterialAutoCompleteTextView
    private lateinit var layEventType: TextInputLayout

    // Gesture
    private lateinit var panelGesture: View
    private lateinit var actvGestureType: MaterialAutoCompleteTextView
    private lateinit var layGestureType: TextInputLayout

    // Battery
    private lateinit var panelBattery: View
    private lateinit var swChargingOnly: MaterialSwitch
    private lateinit var swFilterBattery: MaterialSwitch
    private lateinit var subpanelBatterySlider: View
    private lateinit var lblBatteryLevelValue: TextView
    private lateinit var sliderBatteryLevel: Slider

    // Preview
    private lateinit var txtBlurbPreview: TextView

    // Top Bar Buttons
    private lateinit var btnEventBack: MaterialButton
    private lateinit var btnEventSave: MaterialButton

    data class EventOption(val type: String, val title: String) {
        override fun toString(): String = title
    }

    data class GestureOption(val code: Int?, val name: String?, val title: String) {
        override fun toString(): String = title
    }

    private val eventTypeOptions = listOf(
        EventOption(TaskerConstants.EVENT_TOUCH_GESTURE, "Gesto Táctil / Botón"),
        EventOption(TaskerConstants.EVENT_AI_BUTTON, "Botón Asistente AI"),
        EventOption(TaskerConstants.EVENT_CONNECTED, "Gafas Conectadas"),
        EventOption(TaskerConstants.EVENT_DISCONNECTED, "Gafas Desconectadas"),
        EventOption(TaskerConstants.EVENT_BATTERY_CHANGED, "Nivel de Batería")
    )

    private val gestureOptions = listOf(
        GestureOption(null, null, "Cualquier Gesto"),
        GestureOption(2, "Toque Doble", "Toque Doble"),
        GestureOption(3, "Toque Triple", "Toque Triple"),
        GestureOption(3, "Pulsación Larga", "Pulsación Larga / Botón Hardware"),
        GestureOption(7, "Voz / Wake Word", "Voz / Wake Word")
    )

    private var currentEventType: String = TaskerConstants.EVENT_TOUCH_GESTURE
    private var selectedGestureOption: GestureOption = gestureOptions.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Myvu)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_event)

        initViews()
        setupDropdowns()
        setupListeners()

        val initialBundle = intent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        loadInitialState(initialBundle)
    }

    private fun initViews() {
        actvEventType = findViewById(R.id.actvEventType)
        layEventType = findViewById(R.id.layEventType)

        panelGesture = findViewById(R.id.panelGesture)
        actvGestureType = findViewById(R.id.actvGestureType)
        layGestureType = findViewById(R.id.layGestureType)

        panelBattery = findViewById(R.id.panelBattery)
        swChargingOnly = findViewById(R.id.swChargingOnly)
        swFilterBattery = findViewById(R.id.swFilterBattery)
        subpanelBatterySlider = findViewById(R.id.subpanelBatterySlider)
        lblBatteryLevelValue = findViewById(R.id.lblBatteryLevelValue)
        sliderBatteryLevel = findViewById(R.id.sliderBatteryLevel)

        txtBlurbPreview = findViewById(R.id.txtBlurbPreview)
        btnEventBack = findViewById(R.id.btnEventBack)
        btnEventSave = findViewById(R.id.btnEventSave)
    }

    private fun setupDropdowns() {
        val eventAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            eventTypeOptions
        )
        actvEventType.setAdapter(eventAdapter)
        actvEventType.setOnItemClickListener { _, _, position, _ ->
            val selected = eventTypeOptions[position]
            selectEventType(selected.type)
        }

        val gestureAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            gestureOptions
        )
        actvGestureType.setAdapter(gestureAdapter)
        actvGestureType.setOnItemClickListener { _, _, position, _ ->
            selectedGestureOption = gestureOptions[position]
            updateBlurbPreview()
        }
    }

    private fun selectEventType(type: String) {
        currentEventType = type
        val option = eventTypeOptions.find { it.type == type } ?: eventTypeOptions.first()
        actvEventType.setText(option.title, false)

        panelGesture.visibility = if (type == TaskerConstants.EVENT_TOUCH_GESTURE) View.VISIBLE else View.GONE
        panelBattery.visibility = if (type == TaskerConstants.EVENT_BATTERY_CHANGED) View.VISIBLE else View.GONE

        updateBlurbPreview()
    }

    private fun setupListeners() {
        btnEventBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnEventSave.setOnClickListener {
            saveAndFinish()
        }

        swChargingOnly.setOnCheckedChangeListener { _, _ -> updateBlurbPreview() }

        swFilterBattery.setOnCheckedChangeListener { _, isChecked ->
            subpanelBatterySlider.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateBlurbPreview()
        }

        sliderBatteryLevel.addOnChangeListener { _, value, _ ->
            lblBatteryLevelValue.text = "Nivel: ${value.toInt()}%"
            updateBlurbPreview()
        }
    }

    private fun loadInitialState(bundle: Bundle?) {
        if (bundle != null) {
            val event = TaskerBundleManager.parseEvent(bundle)
            val type = if (event.eventType.isNotBlank()) event.eventType else TaskerConstants.EVENT_TOUCH_GESTURE
            selectEventType(type)

            when (type) {
                TaskerConstants.EVENT_TOUCH_GESTURE -> {
                    val matchingGesture = gestureOptions.find {
                        it.name.equals(event.gestureName, ignoreCase = true) ||
                                (it.code != null && it.code == event.gestureCode)
                    } ?: gestureOptions.first()
                    selectedGestureOption = matchingGesture
                    actvGestureType.setText(matchingGesture.title, false)
                }
                TaskerConstants.EVENT_BATTERY_CHANGED -> {
                    swChargingOnly.isChecked = event.isCharging == true
                    if (event.batteryLevel != null) {
                        swFilterBattery.isChecked = true
                        subpanelBatterySlider.visibility = View.VISIBLE
                        val v = event.batteryLevel.toFloat().coerceIn(0f, 100f)
                        sliderBatteryLevel.value = v
                        lblBatteryLevelValue.text = "Nivel: ${v.toInt()}%"
                    } else {
                        swFilterBattery.isChecked = false
                        subpanelBatterySlider.visibility = View.GONE
                    }
                }
            }
        } else {
            selectEventType(TaskerConstants.EVENT_TOUCH_GESTURE)
            selectedGestureOption = gestureOptions.first()
            actvGestureType.setText(selectedGestureOption.title, false)
            swChargingOnly.isChecked = false
            swFilterBattery.isChecked = false
            subpanelBatterySlider.visibility = View.GONE
            sliderBatteryLevel.value = 20f
            lblBatteryLevelValue.text = "Nivel: 20%"
        }

        updateBlurbPreview()
    }

    private fun buildCurrentEvent(): TaskerEvent {
        return when (currentEventType) {
            TaskerConstants.EVENT_TOUCH_GESTURE -> {
                TaskerEvent(
                    eventType = TaskerConstants.EVENT_TOUCH_GESTURE,
                    gestureCode = selectedGestureOption.code,
                    gestureName = selectedGestureOption.name
                )
            }
            TaskerConstants.EVENT_AI_BUTTON -> {
                TaskerEvent(
                    eventType = TaskerConstants.EVENT_AI_BUTTON,
                    buttonCode = 3
                )
            }
            TaskerConstants.EVENT_CONNECTED -> {
                TaskerEvent(
                    eventType = TaskerConstants.EVENT_CONNECTED,
                    connectionState = "CONNECTED"
                )
            }
            TaskerConstants.EVENT_DISCONNECTED -> {
                TaskerEvent(
                    eventType = TaskerConstants.EVENT_DISCONNECTED,
                    connectionState = "DISCONNECTED"
                )
            }
            TaskerConstants.EVENT_BATTERY_CHANGED -> {
                TaskerEvent(
                    eventType = TaskerConstants.EVENT_BATTERY_CHANGED,
                    batteryLevel = if (swFilterBattery.isChecked) sliderBatteryLevel.value.toInt() else null,
                    isCharging = if (swChargingOnly.isChecked) true else null
                )
            }
            else -> TaskerEvent(eventType = currentEventType)
        }
    }

    private fun updateBlurbPreview() {
        val event = buildCurrentEvent()
        txtBlurbPreview.text = TaskerBundleManager.generateEventBlurb(event)
    }

    private fun saveAndFinish() {
        val event = buildCurrentEvent()
        val bundle = TaskerBundleManager.buildEventBundle(event)
        val blurb = TaskerBundleManager.generateEventBlurb(event)

        val resultIntent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
            putExtra(TaskerConstants.EXTRA_BLURB, blurb)
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
