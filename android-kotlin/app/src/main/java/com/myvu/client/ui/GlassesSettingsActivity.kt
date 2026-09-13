package com.myvu.client.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.myvu.client.R
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.Prefs
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.data.CommonDeviceActions
import com.myvu.client.database.AppDatabase
import com.myvu.client.service.BluetoothDeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated settings and gesture customization activity for Meizu MYVU AR Smart Glasses.
 */
class GlassesSettingsActivity : AppCompatActivity() {

    private lateinit var topBar: View
    private lateinit var btnBack: ImageButton
    private lateinit var btnSave: MaterialButton
    private lateinit var txtGlassesName: TextView
    private lateinit var txtGlassesMac: TextView
    private lateinit var txtGlassesBattery: TextView

    private lateinit var spinnerTap: Spinner
    private lateinit var spinnerDoubleTap: Spinner
    private lateinit var spinnerTripleTap: Spinner
    private lateinit var spinnerSwipeForward: Spinner
    private lateinit var spinnerSwipeBackward: Spinner
    private lateinit var spinnerLongPress: Spinner
    private lateinit var spinnerActionButton: Spinner
    private lateinit var spinnerNotificationMode: Spinner
    private lateinit var txtNotificationModeDescription: TextView
    private lateinit var sliderHudBrightness: Slider
    private lateinit var txtBrightnessLabel: TextView
    private lateinit var sliderGlassesVolume: Slider
    private lateinit var txtGlassesVolumeLabel: TextView
    private lateinit var sliderStandbyPos: Slider
    private lateinit var txtStandbyPosLabel: TextView
    private lateinit var sliderScreenOff: Slider
    private lateinit var txtScreenOffLabel: TextView
    private lateinit var sliderNotifDuration: Slider
    private lateinit var txtNotifDurationLabel: TextView
    private lateinit var btnOpenTrackpad: MaterialButton

    private lateinit var btnAiResponseModeGroup: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var swContinuousDialogue: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var swVoiceWakeup: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var swForceGeminiSco: com.google.android.material.materialswitch.MaterialSwitch

    private var targetMac: String = ""
    private var currentDevice: BluetoothDeviceEntity? = null

    companion object {
        const val EXTRA_MAC = "DEVICE_MAC"
        const val EXTRA_NAME = "DEVICE_NAME"

        fun open(context: Context, mac: String = "", name: String = "") {
            start(context, mac, name)
        }

        fun start(context: Context, mac: String? = null, name: String? = null) {
            val intent = Intent(context, GlassesSettingsActivity::class.java).apply {
                if (!mac.isNullOrEmpty()) putExtra(EXTRA_MAC, mac)
                if (!name.isNullOrEmpty()) putExtra(EXTRA_NAME, name)
            }
            context.startActivity(intent)
        }

        private fun describePos(pos: Int): String = when (pos) {
            0 -> "Centro (0)"
            1 -> "Superior (1)"
            2 -> "Inferior (2)"
            3 -> "Lateral (3)"
            else -> "Posición: $pos"
        }

        private fun formatBrightnessLabel(level: Int): String =
            "Brillo de Pantalla HUD: Nivel $level (${level * 20}%)"

        private fun formatVolumeLabel(vol: Int): String =
            "Volumen de las Gafas: Nivel $vol"

        private fun formatScreenOffLabel(sec: Int): String =
            "Tiempo de Pantalla Activa: ${sec}s"

        private fun formatNotifDurationLabel(sec: Int): String =
            "Duración Notificaciones en HUD: ${sec}s"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glasses_settings)

        topBar = findViewById(R.id.topBar)
        EdgeToEdgeHelper.setupEdgeToEdge(this, topBar)

        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        txtGlassesName = findViewById(R.id.txtGlassesName)
        txtGlassesMac = findViewById(R.id.txtGlassesMac)
        txtGlassesBattery = findViewById(R.id.txtGlassesBattery)

        spinnerTap = findViewById(R.id.spinnerTap)
        spinnerDoubleTap = findViewById(R.id.spinnerDoubleTap)
        spinnerTripleTap = findViewById(R.id.spinnerTripleTap)
        spinnerSwipeForward = findViewById(R.id.spinnerSwipeForward)
        spinnerSwipeBackward = findViewById(R.id.spinnerSwipeBackward)
        spinnerLongPress = findViewById(R.id.spinnerLongPress)
        spinnerActionButton = findViewById(R.id.spinnerActionButton)
        spinnerNotificationMode = findViewById(R.id.spinnerNotificationMode)
        txtNotificationModeDescription = findViewById(R.id.txtNotificationModeDescription)

        // Pantalla y Audio
        sliderHudBrightness = findViewById(R.id.sliderHudBrightness)
        txtBrightnessLabel = findViewById(R.id.txtBrightnessLabel)
        sliderGlassesVolume = findViewById(R.id.sliderGlassesVolume)
        txtGlassesVolumeLabel = findViewById(R.id.txtGlassesVolumeLabel)
        sliderStandbyPos = findViewById(R.id.sliderStandbyPos)
        txtStandbyPosLabel = findViewById(R.id.txtStandbyPosLabel)
        sliderScreenOff = findViewById(R.id.sliderScreenOff)
        txtScreenOffLabel = findViewById(R.id.txtScreenOffLabel)
        sliderNotifDuration = findViewById(R.id.sliderNotifDuration)
        txtNotifDurationLabel = findViewById(R.id.txtNotifDurationLabel)
        btnOpenTrackpad = findViewById(R.id.btnOpenTrackpad)

        // Escucha y Batería
        btnAiResponseModeGroup = findViewById(R.id.btnAiResponseModeGroup)
        swContinuousDialogue = findViewById(R.id.swContinuousDialogue)
        swVoiceWakeup = findViewById(R.id.swVoiceWakeup)
        swForceGeminiSco = findViewById(R.id.swForceGeminiSco)

        targetMac = intent.getStringExtra(EXTRA_MAC) ?: ""

        setupSpinners()

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveSettings() }

        // Live preview listeners for screen/audio sliders
        sliderHudBrightness.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            txtBrightnessLabel.text = formatBrightnessLabel(v)
            if (fromUser) {
                com.myvu.client.core.GlassesConfig.setBrightness(this, v)
            }
        }

        sliderGlassesVolume.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            txtGlassesVolumeLabel.text = formatVolumeLabel(v)
            if (fromUser) {
                com.myvu.client.core.GlassesConfig.setVolume(this, v)
            }
        }

        sliderStandbyPos.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            txtStandbyPosLabel.text = "Posición del Dashboard en FOV: ${describePos(v)}"
            if (fromUser) {
                com.myvu.client.core.GlassesConfig.setStandbyPosition(this, v)
            }
        }

        sliderScreenOff.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            txtScreenOffLabel.text = formatScreenOffLabel(v)
            if (fromUser) {
                com.myvu.client.core.GlassesConfig.setScreenOffTime(this, v)
            }
        }

        sliderNotifDuration.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            txtNotifDurationLabel.text = formatNotifDurationLabel(v)
            if (fromUser) {
                com.myvu.client.core.GlassesConfig.setNotificationDuration(this, v)
            }
        }

        btnOpenTrackpad.setOnClickListener {
            startActivity(Intent(this, TrackpadActivity::class.java))
        }

        loadDevice()
    }

    private fun setupSpinners() {
        val labels = CommonDeviceActions.getLabels()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)

        spinnerTap.adapter = adapter
        spinnerDoubleTap.adapter = adapter
        spinnerTripleTap.adapter = adapter
        spinnerSwipeForward.adapter = adapter
        spinnerSwipeBackward.adapter = adapter
        spinnerLongPress.adapter = adapter

        val actionBtnLabels = listOf(
            "Agente IA de Gafas (Aura STT+API - Mantener)",
            "Lanzar Asistente Gemini (Mantener)",
            "Lanzar Gemini Live (Voz Continua - Mantener)",
            "Asistente del Teléfono (Google - Mantener)"
        )
        val actionBtnAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actionBtnLabels)
        spinnerActionButton.adapter = actionBtnAdapter

        val notifLabels = com.myvu.client.data.DeviceNotificationMode.getLabels()
        val notifAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, notifLabels)
        spinnerNotificationMode.adapter = notifAdapter
        spinnerNotificationMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(position)
                txtNotificationModeDescription.text = "${mode.displayName}: ${mode.description}"
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadDevice() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@GlassesSettingsActivity).bluetoothDeviceDao()
            val dev = withContext(Dispatchers.IO) {
                val byMac = if (targetMac.isNotBlank()) dao.getDevice(targetMac) else null
                byMac ?: run {
                    val configuredMac = Prefs.targetMac(this@GlassesSettingsActivity)
                    if (configuredMac.isNotBlank()) dao.getDevice(configuredMac) else null
                } ?: dao.getConnectedDeviceByType(BluetoothDeviceType.SMART_GLASSES.name)
                  ?: dao.getAllDevices().find { 
                      it.deviceType == BluetoothDeviceType.SMART_GLASSES.name ||
                      it.name.contains("MYVU", ignoreCase = true) ||
                      it.name.contains("Glasses", ignoreCase = true)
                  }
            }
            currentDevice = dev

            val connBattery = com.myvu.client.service.MyvuService.activeConnection()?.glassesInfo()?.battery?.takeIf { it in 0..100 }
            val realBattery = dev?.batteryLevel?.takeIf { it in 0..100 } ?: connBattery

            if (dev != null) {
                targetMac = dev.macAddress
                txtGlassesName.text = dev.name
                txtGlassesMac.text = "MAC: ${dev.macAddress}"
                txtGlassesBattery.text = if (realBattery != null) "$realBattery%" else "--"
            } else {
                val fallbackMac = Prefs.targetMac(this@GlassesSettingsActivity).ifBlank { targetMac }
                if (fallbackMac.isNotBlank()) targetMac = fallbackMac
                txtGlassesName.text = intent.getStringExtra(EXTRA_NAME) ?: "Meizu MYVU AR"
                txtGlassesMac.text = if (targetMac.isNotBlank()) "MAC: $targetMac" else "Gafas AR vinculadas"
                txtGlassesBattery.text = if (realBattery != null) "$realBattery%" else "--"
            }

            // Sync with current Prefs and Entity
            val tap = dev?.tap1Action ?: Prefs.touchpadTapAction(this@GlassesSettingsActivity)
            val doubleTap = dev?.tap2Action ?: Prefs.touchpadDoubleTapAction(this@GlassesSettingsActivity)
            val tripleTap = dev?.tap3Action ?: Prefs.touchpadTripleTapAction(this@GlassesSettingsActivity)
            val swipeForward = dev?.swipeForwardAction ?: Prefs.touchpadSwipeForwardAction(this@GlassesSettingsActivity)
            val swipeBackward = dev?.swipeBackwardAction ?: Prefs.touchpadSwipeBackwardAction(this@GlassesSettingsActivity)
            val longPress = dev?.longPressAction ?: Prefs.touchpadLongPressAction(this@GlassesSettingsActivity)
            val actionBtn = dev?.actionButtonAction ?: Prefs.glassesActionButtonAction(this@GlassesSettingsActivity)

            spinnerTap.setSelection(CommonDeviceActions.getIndexForAction(tap))
            spinnerDoubleTap.setSelection(CommonDeviceActions.getIndexForAction(doubleTap))
            spinnerTripleTap.setSelection(CommonDeviceActions.getIndexForAction(tripleTap))
            spinnerSwipeForward.setSelection(CommonDeviceActions.getIndexForAction(swipeForward))
            spinnerSwipeBackward.setSelection(CommonDeviceActions.getIndexForAction(swipeBackward))
            spinnerLongPress.setSelection(CommonDeviceActions.getIndexForAction(longPress))

            val actionBtnPos = when (actionBtn) {
                "LAUNCH_GEMINI" -> 1
                "LAUNCH_GEMINI_LIVE" -> 2
                "LAUNCH_PHONE_ASSISTANT" -> 3
                else -> 0
            }
            spinnerActionButton.setSelection(actionBtnPos)

            val currentNotifMode = dev?.notificationMode ?: com.myvu.client.data.DeviceNotificationMode.BOTH.name
            val notifPos = com.myvu.client.data.DeviceNotificationMode.getIndex(currentNotifMode)
            spinnerNotificationMode.setSelection(notifPos)
            val notifObj = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(notifPos)
            txtNotificationModeDescription.text = "${notifObj.displayName}: ${notifObj.description}"

            // 1. Brillo (1 a 5)
            val currentBrightness = dev?.hudBrightness?.let { (it / 20).coerceIn(1, 5) }
                ?: com.myvu.client.core.GlassesConfig.getBrightness(this@GlassesSettingsActivity)
            sliderHudBrightness.value = currentBrightness.toFloat().coerceIn(1f, 5f)
            txtBrightnessLabel.text = formatBrightnessLabel(currentBrightness)

            // 2. Volumen (0 a 15)
            val currentVolume = com.myvu.client.core.GlassesConfig.getVolume(this@GlassesSettingsActivity)
            sliderGlassesVolume.value = currentVolume.toFloat().coerceIn(0f, 15f)
            txtGlassesVolumeLabel.text = formatVolumeLabel(currentVolume)

            // 3. Posición FOV (0 a 3)
            val currentStandby = com.myvu.client.core.GlassesConfig.getStandbyPosition(this@GlassesSettingsActivity)
            sliderStandbyPos.value = currentStandby.toFloat().coerceIn(0f, 3f)
            txtStandbyPosLabel.text = "Posición del Dashboard en FOV: ${describePos(currentStandby)}"

            // 4. Tiempo Pantalla Activa (3 a 60s)
            val currentScreenOff = com.myvu.client.core.GlassesConfig.getScreenOffTime(this@GlassesSettingsActivity)
            sliderScreenOff.value = currentScreenOff.toFloat().coerceIn(3f, 60f)
            txtScreenOffLabel.text = formatScreenOffLabel(currentScreenOff)

            // 5. Duración Notificaciones HUD (1 a 30s)
            val currentNotifDuration = com.myvu.client.core.GlassesConfig.getNotificationDuration(this@GlassesSettingsActivity)
            sliderNotifDuration.value = currentNotifDuration.toFloat().coerceIn(1f, 30f)
            txtNotifDurationLabel.text = formatNotifDurationLabel(currentNotifDuration)

            // 6. Modo de Respuesta IA
            when (Prefs.aiResponseMode(this@GlassesSettingsActivity)) {
                com.myvu.client.ai.AiResponseMode.VOICE_ONLY.id -> btnAiResponseModeGroup.check(R.id.btnAiResponseVoice)
                com.myvu.client.ai.AiResponseMode.VISUAL_ONLY.id -> btnAiResponseModeGroup.check(R.id.btnAiResponseVisual)
                else -> btnAiResponseModeGroup.check(R.id.btnAiResponseBoth)
            }

            // 7. Switches de escucha, wake word y SCO
            swContinuousDialogue.isChecked = currentDevice?.activeListeningEnabled ?: false
            swVoiceWakeup.isChecked = Prefs.voiceWakeupEnabled(this@GlassesSettingsActivity)
            swForceGeminiSco.isChecked = Prefs.isGeminiForceScoEnabled(this@GlassesSettingsActivity)
        }
    }

    private fun saveSettings() {
        val tapAction = CommonDeviceActions.getActionId(spinnerTap.selectedItemPosition)
        val doubleTapAction = CommonDeviceActions.getActionId(spinnerDoubleTap.selectedItemPosition)
        val tripleTapAction = CommonDeviceActions.getActionId(spinnerTripleTap.selectedItemPosition)
        val swipeForwardAction = CommonDeviceActions.getActionId(spinnerSwipeForward.selectedItemPosition)
        val swipeBackwardAction = CommonDeviceActions.getActionId(spinnerSwipeBackward.selectedItemPosition)
        val longPressAction = CommonDeviceActions.getActionId(spinnerLongPress.selectedItemPosition)

        // Pantalla y Audio - Hardware Sync
        val brightness = sliderHudBrightness.value.toInt().coerceIn(1, 5)
        com.myvu.client.core.GlassesConfig.setBrightness(this, brightness)

        val volume = sliderGlassesVolume.value.toInt().coerceIn(0, 15)
        com.myvu.client.core.GlassesConfig.setVolume(this, volume)

        val standbyPos = sliderStandbyPos.value.toInt().coerceIn(0, 3)
        com.myvu.client.core.GlassesConfig.setStandbyPosition(this, standbyPos)

        val screenOff = sliderScreenOff.value.toInt().coerceIn(3, 60)
        com.myvu.client.core.GlassesConfig.setScreenOffTime(this, screenOff)

        val notifDuration = sliderNotifDuration.value.toInt().coerceIn(1, 30)
        com.myvu.client.core.GlassesConfig.setNotificationDuration(this, notifDuration)

        // Modo de Respuesta de IA
        val chosenResponseMode = when (btnAiResponseModeGroup.checkedButtonId) {
            R.id.btnAiResponseVoice -> com.myvu.client.ai.AiResponseMode.VOICE_ONLY.id
            R.id.btnAiResponseVisual -> com.myvu.client.ai.AiResponseMode.VISUAL_ONLY.id
            else -> com.myvu.client.ai.AiResponseMode.VOICE_AND_VISUAL.id
        }
        Prefs.setAiResponseMode(this, chosenResponseMode)

        // Escucha y Wake word — sync to Prefs for backward-compat; per-device stored in entity below
        val contDialogue = swContinuousDialogue.isChecked
        Prefs.setContinuousDialogueEnabled(this, contDialogue)

        val voiceWakeup = swVoiceWakeup.isChecked
        Prefs.setVoiceWakeupEnabled(this, voiceWakeup)

        // Hardware Direct Dispatch (settings like brightness, volume, etc. already sent via GlassesConfig)
        try {
            val activeConn = com.myvu.client.service.MyvuService.activeConnection()
            activeConn?.setMusicTpControl(true)
            val payload = com.myvu.client.app.feature.AiProtocol.assistantConfig(
                lowPowerWakeupEnabled = voiceWakeup,
                continuousDialogueEnabled = contDialogue
            )
            activeConn?.sendAction(
                payload,
                com.myvu.client.app.feature.AiProtocol.PKG,
                com.myvu.client.app.feature.AiProtocol.PKG
            )
        } catch (_: Exception) {}

        // Enrutamiento SCO Gemini
        Prefs.setGeminiForceScoEnabled(this, swForceGeminiSco.isChecked)

        val actionBtnIndex = spinnerActionButton.selectedItemPosition
        val actionBtn = when (actionBtnIndex) {
            1 -> "LAUNCH_GEMINI"
            2 -> "LAUNCH_GEMINI_LIVE"
            3 -> "LAUNCH_PHONE_ASSISTANT"
            else -> "VOICE_AI_FIXED"
        }

        val chosenNotifMode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(spinnerNotificationMode.selectedItemPosition).id

        val resolvedMac = targetMac.ifBlank {
            Prefs.targetMac(this).ifBlank {
                currentDevice?.macAddress ?: "MYVU-GLASSES-01"
            }
        }
        targetMac = resolvedMac
        Prefs.setTargetMac(this, resolvedMac)

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@GlassesSettingsActivity).bluetoothDeviceDao()
            val existing = withContext(Dispatchers.IO) {
                dao.getDevice(resolvedMac) ?: dao.getAllDevices().find {
                    it.deviceType == BluetoothDeviceType.SMART_GLASSES.name ||
                    it.name.contains("MYVU", ignoreCase = true)
                }
            }

            val entityToSave = (existing ?: currentDevice ?: BluetoothDeviceEntity(
                macAddress = resolvedMac,
                name = txtGlassesName.text.toString().ifBlank { "Meizu MYVU AR" },
                deviceType = BluetoothDeviceType.SMART_GLASSES.name
            )).copy(
                macAddress = existing?.macAddress ?: resolvedMac,
                name = txtGlassesName.text.toString().ifBlank { "Meizu MYVU AR" },
                deviceType = BluetoothDeviceType.SMART_GLASSES.name,
                tap1Action = tapAction,
                tap2Action = doubleTapAction,
                tap3Action = tripleTapAction,
                swipeForwardAction = swipeForwardAction,
                swipeBackwardAction = swipeBackwardAction,
                longPressAction = longPressAction,
                actionButtonAction = actionBtn,
                hudBrightness = brightness * 20,
                notificationMode = chosenNotifMode,
                activeListeningEnabled = contDialogue
            )

            withContext(Dispatchers.IO) {
                dao.insertOrUpdate(entityToSave)
            }
            currentDevice = entityToSave

            BluetoothDeviceManager.getInstance(this@GlassesSettingsActivity).updateGlassesGestures(
                mac = resolvedMac,
                tap = tapAction,
                doubleTap = doubleTapAction,
                tripleTap = tripleTapAction,
                swipeForward = swipeForwardAction,
                swipeBackward = swipeBackwardAction,
                longPress = longPressAction,
                actionButton = actionBtn,
                hudBrightness = brightness * 20,
                notificationMode = chosenNotifMode,
                activeListening = contDialogue
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(this@GlassesSettingsActivity, "Ajustes de pantalla, audio y gestos guardados y aplicados", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
