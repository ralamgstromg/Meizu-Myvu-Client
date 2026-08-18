package com.myvu.client.plugin.tasker.action

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.myvu.client.R
import com.myvu.client.core.GlassesConfig
import com.myvu.client.plugin.tasker.TaskerAction
import com.myvu.client.plugin.tasker.TaskerBundleManager
import com.myvu.client.plugin.tasker.TaskerConstants

class TaskerActionActivity : AppCompatActivity() {

    private lateinit var actvActionType: MaterialAutoCompleteTextView
    private lateinit var layActionType: TextInputLayout

    // HUD
    private lateinit var panelHud: View
    private lateinit var layHudTitle: TextInputLayout
    private lateinit var txtHudTitle: TextInputEditText
    private lateinit var layHudContent: TextInputLayout
    private lateinit var txtHudContent: TextInputEditText

    // Teleprompter
    private lateinit var panelTeleprompter: View
    private lateinit var layPrompterTitle: TextInputLayout
    private lateinit var txtPrompterTitle: TextInputEditText
    private lateinit var layPrompterContent: TextInputLayout
    private lateinit var txtPrompterContent: TextInputEditText

    // Brightness
    private lateinit var panelBrightness: View
    private lateinit var lblBrightnessValue: android.widget.TextView
    private lateinit var sliderBrightness: Slider

    // Volume
    private lateinit var panelVolume: View
    private lateinit var lblVolumeValue: android.widget.TextView
    private lateinit var sliderVolume: Slider

    // System Settings
    private lateinit var panelSystem: View
    private lateinit var subpanelWifi: View
    private lateinit var swWifi: MaterialSwitch
    private lateinit var subpanelZenMode: View
    private lateinit var swZenMode: MaterialSwitch
    private lateinit var subpanelAirMode: View
    private lateinit var swAirMode: MaterialSwitch
    private lateinit var subpanelStandbyPos: View
    private lateinit var lblStandbyPosValue: android.widget.TextView
    private lateinit var sliderStandbyPos: Slider

    // Raw JSON
    private lateinit var panelRaw: View
    private lateinit var layRawJson: TextInputLayout
    private lateinit var txtRawJson: TextInputEditText

    // Preview
    private lateinit var txtBlurbPreview: android.widget.TextView

    // Top Bar Buttons
    private lateinit var btnActionBack: MaterialButton
    private lateinit var btnActionSave: MaterialButton

    private var currentActionType: String = TaskerConstants.TYPE_SHOW_HUD

    private val actionTypeOptions = listOf(
        ActionOption(TaskerConstants.TYPE_SHOW_HUD, "Mostrar Mensaje HUD / Notificación"),
        ActionOption(TaskerConstants.TYPE_SHOW_TELEPROMPTER, "Abrir Teleprompter"),
        ActionOption(TaskerConstants.TYPE_SET_BRIGHTNESS, "Ajustar Brillo"),
        ActionOption(TaskerConstants.TYPE_SET_VOLUME, "Ajustar Volumen"),
        ActionOption(TaskerConstants.TYPE_TOGGLE_WIFI, "Alternar WiFi"),
        ActionOption(TaskerConstants.TYPE_SET_ZEN_MODE, "Modo Zen (No molestar)"),
        ActionOption(TaskerConstants.TYPE_SET_AIR_MODE, "Modo Air"),
        ActionOption(TaskerConstants.TYPE_SET_STANDBY_POS, "Posición Standby (FOV)"),
        ActionOption(TaskerConstants.TYPE_SEND_RAW, "Enviar Comando JSON Raw")
    )

    private data class ActionOption(val type: String, val title: String) {
        override fun toString(): String = title
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Myvu)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_action)

        initViews()
        setupActionTypeDropdown()
        setupListeners()

        val initialBundle = intent.getBundleExtra(TaskerConstants.EXTRA_BUNDLE)
        loadInitialState(initialBundle)
    }

    private fun initViews() {
        actvActionType = findViewById(R.id.actvActionType)
        layActionType = findViewById(R.id.layActionType)

        panelHud = findViewById(R.id.panelHud)
        layHudTitle = findViewById(R.id.layHudTitle)
        txtHudTitle = findViewById(R.id.txtHudTitle)
        layHudContent = findViewById(R.id.layHudContent)
        txtHudContent = findViewById(R.id.txtHudContent)

        panelTeleprompter = findViewById(R.id.panelTeleprompter)
        layPrompterTitle = findViewById(R.id.layPrompterTitle)
        txtPrompterTitle = findViewById(R.id.txtPrompterTitle)
        layPrompterContent = findViewById(R.id.layPrompterContent)
        txtPrompterContent = findViewById(R.id.txtPrompterContent)

        panelBrightness = findViewById(R.id.panelBrightness)
        lblBrightnessValue = findViewById(R.id.lblBrightnessValue)
        sliderBrightness = findViewById(R.id.sliderBrightness)

        panelVolume = findViewById(R.id.panelVolume)
        lblVolumeValue = findViewById(R.id.lblVolumeValue)
        sliderVolume = findViewById(R.id.sliderVolume)

        panelSystem = findViewById(R.id.panelSystem)
        subpanelWifi = findViewById(R.id.subpanelWifi)
        swWifi = findViewById(R.id.swWifi)
        subpanelZenMode = findViewById(R.id.subpanelZenMode)
        swZenMode = findViewById(R.id.swZenMode)
        subpanelAirMode = findViewById(R.id.subpanelAirMode)
        swAirMode = findViewById(R.id.swAirMode)
        subpanelStandbyPos = findViewById(R.id.subpanelStandbyPos)
        lblStandbyPosValue = findViewById(R.id.lblStandbyPosValue)
        sliderStandbyPos = findViewById(R.id.sliderStandbyPos)

        panelRaw = findViewById(R.id.panelRaw)
        layRawJson = findViewById(R.id.layRawJson)
        txtRawJson = findViewById(R.id.txtRawJson)

        txtBlurbPreview = findViewById(R.id.txtBlurbPreview)
        btnActionBack = findViewById(R.id.btnActionBack)
        btnActionSave = findViewById(R.id.btnActionSave)
    }

    private fun setupActionTypeDropdown() {
        actvActionType.isFocusable = false
        actvActionType.isClickable = true
        actvActionType.isCursorVisible = false

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            actionTypeOptions
        )
        actvActionType.setAdapter(adapter)
        actvActionType.setOnItemClickListener { _, _, position, _ ->
            val selected = actionTypeOptions[position]
            selectActionType(selected.type)
        }

        val clickListener = View.OnClickListener {
            showActionTypePicker()
        }
        actvActionType.setOnClickListener(clickListener)
        layActionType.setOnClickListener(clickListener)
        layActionType.setEndIconOnClickListener(clickListener)
    }

    private fun showActionTypePicker() {
        val titles = actionTypeOptions.map { it.title }.toTypedArray()
        val currentIndex = actionTypeOptions.indexOfFirst { it.type == currentActionType }.coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tipo de Acción")
            .setSingleChoiceItems(titles, currentIndex) { dialog, which ->
                val selected = actionTypeOptions[which]
                selectActionType(selected.type)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun selectActionType(type: String) {
        currentActionType = type
        val option = actionTypeOptions.find { it.type == type } ?: actionTypeOptions.first()
        actvActionType.setText(option.title, false)

        panelHud.visibility = if (type == TaskerConstants.TYPE_SHOW_HUD) View.VISIBLE else View.GONE
        panelTeleprompter.visibility = if (type == TaskerConstants.TYPE_SHOW_TELEPROMPTER) View.VISIBLE else View.GONE
        panelBrightness.visibility = if (type == TaskerConstants.TYPE_SET_BRIGHTNESS) View.VISIBLE else View.GONE
        panelVolume.visibility = if (type == TaskerConstants.TYPE_SET_VOLUME) View.VISIBLE else View.GONE

        val isSystem = type in listOf(
            TaskerConstants.TYPE_TOGGLE_WIFI,
            TaskerConstants.TYPE_SET_ZEN_MODE,
            TaskerConstants.TYPE_SET_AIR_MODE,
            TaskerConstants.TYPE_SET_STANDBY_POS
        )
        panelSystem.visibility = if (isSystem) View.VISIBLE else View.GONE
        subpanelWifi.visibility = if (type == TaskerConstants.TYPE_TOGGLE_WIFI) View.VISIBLE else View.GONE
        subpanelZenMode.visibility = if (type == TaskerConstants.TYPE_SET_ZEN_MODE) View.VISIBLE else View.GONE
        subpanelAirMode.visibility = if (type == TaskerConstants.TYPE_SET_AIR_MODE) View.VISIBLE else View.GONE
        subpanelStandbyPos.visibility = if (type == TaskerConstants.TYPE_SET_STANDBY_POS) View.VISIBLE else View.GONE

        panelRaw.visibility = if (type == TaskerConstants.TYPE_SEND_RAW) View.VISIBLE else View.GONE

        updateBlurbPreview()
    }

    private fun setupListeners() {
        btnActionBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnActionSave.setOnClickListener {
            saveAndFinish()
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateBlurbPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        txtHudTitle.addTextChangedListener(textWatcher)
        txtHudContent.addTextChangedListener(textWatcher)
        txtPrompterTitle.addTextChangedListener(textWatcher)
        txtPrompterContent.addTextChangedListener(textWatcher)
        txtRawJson.addTextChangedListener(textWatcher)

        sliderBrightness.addOnChangeListener { _, value, _ ->
            lblBrightnessValue.text = "Nivel de Brillo: ${value.toInt()} (Rango 1 a 5)"
            updateBlurbPreview()
        }

        sliderVolume.addOnChangeListener { _, value, _ ->
            lblVolumeValue.text = "Nivel de Volumen: ${value.toInt()} (Rango 0 a 15)"
            updateBlurbPreview()
        }

        sliderStandbyPos.addOnChangeListener { _, value, _ ->
            val pos = value.toInt()
            val posDesc = when (pos) {
                0 -> "Centro (0)"
                1 -> "Arriba (1)"
                2 -> "Derecha (2)"
                3 -> "Izquierda (3)"
                else -> "$pos"
            }
            lblStandbyPosValue.text = "Posición Standby FOV: $posDesc"
            updateBlurbPreview()
        }

        swWifi.setOnCheckedChangeListener { _, _ -> updateBlurbPreview() }
        swZenMode.setOnCheckedChangeListener { _, _ -> updateBlurbPreview() }
        swAirMode.setOnCheckedChangeListener { _, _ -> updateBlurbPreview() }
    }

    private fun loadInitialState(bundle: Bundle?) {
        if (bundle != null) {
            val action = TaskerBundleManager.parseAction(bundle)
            val type = if (action.type.isNotBlank()) action.type else TaskerConstants.TYPE_SHOW_HUD
            selectActionType(type)

            when (type) {
                TaskerConstants.TYPE_SHOW_HUD -> {
                    txtHudTitle.setText(action.title ?: "")
                    txtHudContent.setText(action.content ?: "")
                }
                TaskerConstants.TYPE_SHOW_TELEPROMPTER -> {
                    txtPrompterTitle.setText(action.title ?: "")
                    txtPrompterContent.setText(action.content ?: "")
                }
                TaskerConstants.TYPE_SET_BRIGHTNESS -> {
                    val v = (action.valueInt ?: GlassesConfig.DEFAULT_BRIGHTNESS).toFloat().coerceIn(1f, 5f)
                    sliderBrightness.value = v
                    lblBrightnessValue.text = "Nivel de Brillo: ${v.toInt()} (Rango 1 a 5)"
                }
                TaskerConstants.TYPE_SET_VOLUME -> {
                    val v = (action.valueInt ?: GlassesConfig.DEFAULT_VOLUME).toFloat().coerceIn(0f, 15f)
                    sliderVolume.value = v
                    lblVolumeValue.text = "Nivel de Volumen: ${v.toInt()} (Rango 0 a 15)"
                }
                TaskerConstants.TYPE_TOGGLE_WIFI -> {
                    swWifi.isChecked = action.valueBoolean ?: true
                }
                TaskerConstants.TYPE_SET_ZEN_MODE -> {
                    swZenMode.isChecked = action.valueBoolean ?: true
                }
                TaskerConstants.TYPE_SET_AIR_MODE -> {
                    swAirMode.isChecked = action.valueBoolean ?: true
                }
                TaskerConstants.TYPE_SET_STANDBY_POS -> {
                    val v = (action.valueInt ?: GlassesConfig.DEFAULT_STANDBY_POS).toFloat().coerceIn(0f, 3f)
                    sliderStandbyPos.value = v
                    val pos = v.toInt()
                    val posDesc = when (pos) {
                        0 -> "Centro (0)"
                        1 -> "Arriba (1)"
                        2 -> "Derecha (2)"
                        3 -> "Izquierda (3)"
                        else -> "$pos"
                    }
                    lblStandbyPosValue.text = "Posición Standby FOV: $posDesc"
                }
                TaskerConstants.TYPE_SEND_RAW -> {
                    txtRawJson.setText(action.rawJson ?: "")
                }
            }
        } else {
            selectActionType(TaskerConstants.TYPE_SHOW_HUD)
            sliderBrightness.value = GlassesConfig.DEFAULT_BRIGHTNESS.toFloat()
            sliderVolume.value = GlassesConfig.DEFAULT_VOLUME.toFloat()
            sliderStandbyPos.value = GlassesConfig.DEFAULT_STANDBY_POS.toFloat()
            swWifi.isChecked = true
            swZenMode.isChecked = true
            swAirMode.isChecked = true
        }

        updateBlurbPreview()
    }

    private fun buildCurrentAction(): TaskerAction {
        return when (currentActionType) {
            TaskerConstants.TYPE_SHOW_HUD -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SHOW_HUD,
                    title = txtHudTitle.text?.toString()?.trim()?.ifEmpty { null },
                    content = txtHudContent.text?.toString()?.trim()
                )
            }
            TaskerConstants.TYPE_SHOW_TELEPROMPTER -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SHOW_TELEPROMPTER,
                    title = txtPrompterTitle.text?.toString()?.trim()?.ifEmpty { null },
                    content = txtPrompterContent.text?.toString()?.trim()
                )
            }
            TaskerConstants.TYPE_SET_BRIGHTNESS -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SET_BRIGHTNESS,
                    valueInt = sliderBrightness.value.toInt()
                )
            }
            TaskerConstants.TYPE_SET_VOLUME -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SET_VOLUME,
                    valueInt = sliderVolume.value.toInt()
                )
            }
            TaskerConstants.TYPE_TOGGLE_WIFI -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_TOGGLE_WIFI,
                    valueBoolean = swWifi.isChecked
                )
            }
            TaskerConstants.TYPE_SET_ZEN_MODE -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SET_ZEN_MODE,
                    valueBoolean = swZenMode.isChecked
                )
            }
            TaskerConstants.TYPE_SET_AIR_MODE -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SET_AIR_MODE,
                    valueBoolean = swAirMode.isChecked
                )
            }
            TaskerConstants.TYPE_SET_STANDBY_POS -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SET_STANDBY_POS,
                    valueInt = sliderStandbyPos.value.toInt()
                )
            }
            TaskerConstants.TYPE_SEND_RAW -> {
                TaskerAction(
                    type = TaskerConstants.TYPE_SEND_RAW,
                    rawJson = txtRawJson.text?.toString()?.trim()
                )
            }
            else -> TaskerAction(type = currentActionType)
        }
    }

    private fun updateBlurbPreview() {
        val action = buildCurrentAction()
        txtBlurbPreview.text = TaskerBundleManager.generateBlurb(action)
    }

    private fun validateInputs(): Boolean {
        layHudContent.error = null
        layPrompterContent.error = null
        layRawJson.error = null

        when (currentActionType) {
            TaskerConstants.TYPE_SHOW_HUD -> {
                val content = txtHudContent.text?.toString()?.trim()
                if (content.isNullOrEmpty()) {
                    layHudContent.error = "El mensaje no puede estar vacío"
                    return false
                }
            }
            TaskerConstants.TYPE_SHOW_TELEPROMPTER -> {
                val content = txtPrompterContent.text?.toString()?.trim()
                if (content.isNullOrEmpty()) {
                    layPrompterContent.error = "El texto del teleprompter no puede estar vacío"
                    return false
                }
            }
            TaskerConstants.TYPE_SEND_RAW -> {
                val raw = txtRawJson.text?.toString()?.trim()
                if (raw.isNullOrEmpty()) {
                    layRawJson.error = "El comando JSON no puede estar vacío"
                    return false
                }
            }
        }
        return true
    }

    private fun saveAndFinish() {
        if (!validateInputs()) return

        val action = buildCurrentAction()
        val bundle = TaskerBundleManager.buildActionBundle(action)
        val blurb = TaskerBundleManager.generateBlurb(action)

        val resultIntent = Intent().apply {
            putExtra(TaskerConstants.EXTRA_BUNDLE, bundle)
            putExtra(TaskerConstants.EXTRA_BLURB, blurb)

            val hasVariables = TaskerBundleManager.containsVariables(action.title) ||
                    TaskerBundleManager.containsVariables(action.content) ||
                    TaskerBundleManager.containsVariables(action.rawJson) ||
                    TaskerBundleManager.containsVariables(action.valueString)

            if (hasVariables) {
                putExtra(
                    TaskerConstants.EXTRA_VARIABLE_REPLACE_KEYS,
                    TaskerBundleManager.getVariableReplaceKeys()
                )
            }
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
