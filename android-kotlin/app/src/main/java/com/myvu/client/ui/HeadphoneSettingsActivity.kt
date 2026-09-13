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
import com.google.android.material.materialswitch.MaterialSwitch
import com.myvu.client.R
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.myvu.client.data.CommonDeviceActions

class HeadphoneSettingsActivity : AppCompatActivity() {

    private lateinit var topBar: View
    private lateinit var btnBack: ImageButton
    private lateinit var btnSave: MaterialButton
    private lateinit var txtHeadphoneName: TextView
    private lateinit var txtHeadphoneMac: TextView
    private lateinit var txtHeadphoneBattery: TextView
    private lateinit var spinnerTap1: Spinner
    private lateinit var spinnerTap2: Spinner
    private lateinit var spinnerTap3: Spinner
    private lateinit var spinnerLongPress: Spinner
    private lateinit var switchTts: MaterialSwitch
    private lateinit var switchAutoReadNotifications: MaterialSwitch
    private lateinit var switchActiveListening: MaterialSwitch
    private lateinit var spinnerNotificationMode: Spinner
    private lateinit var txtNotificationModeDescription: TextView
    private lateinit var btnTestVoice: MaterialButton

    private var targetMac: String = ""
    private var currentDevice: BluetoothDeviceEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headphone_settings)

        topBar = findViewById(R.id.topBar)
        EdgeToEdgeHelper.setupEdgeToEdge(this, topBar)

        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        txtHeadphoneName = findViewById(R.id.txtHeadphoneName)
        txtHeadphoneMac = findViewById(R.id.txtHeadphoneMac)
        txtHeadphoneBattery = findViewById(R.id.txtHeadphoneBattery)
        spinnerTap1 = findViewById(R.id.spinnerTap1)
        spinnerTap2 = findViewById(R.id.spinnerTap2)
        spinnerTap3 = findViewById(R.id.spinnerTap3)
        spinnerLongPress = findViewById(R.id.spinnerLongPress)
        switchTts = findViewById(R.id.switchTts)
        switchAutoReadNotifications = findViewById(R.id.switchAutoReadNotifications)
        switchActiveListening = findViewById(R.id.switchActiveListening)
        spinnerNotificationMode = findViewById(R.id.spinnerNotificationMode)
        txtNotificationModeDescription = findViewById(R.id.txtNotificationModeDescription)
        btnTestVoice = findViewById(R.id.btnTestVoice)

        targetMac = intent.getStringExtra(EXTRA_MAC) ?: ""

        setupSpinners()

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveSettings() }

        btnTestVoice.setOnClickListener {
            TextToSpeechHelper.speak("Hola, la síntesis de voz en tus auriculares está configurada y lista para recibir comandos.")
            Toast.makeText(this, "Reproduciendo prueba de voz...", Toast.LENGTH_SHORT).show()
        }

        loadDevice()
    }

    private fun setupSpinners() {
        val labels = CommonDeviceActions.getLabels()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinnerTap1.adapter = adapter
        spinnerTap2.adapter = adapter
        spinnerTap3.adapter = adapter
        spinnerLongPress.adapter = adapter

        val notifLabels = com.myvu.client.data.DeviceNotificationMode.getLabels()
        val notifAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, notifLabels)
        spinnerNotificationMode.adapter = notifAdapter
        spinnerNotificationMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(position)
                txtNotificationModeDescription.text = "${mode.displayName}: ${mode.description}"
                if (switchAutoReadNotifications.isChecked != mode.isAudioNotificationEnabled()) {
                    switchAutoReadNotifications.isChecked = mode.isAudioNotificationEnabled()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        switchAutoReadNotifications.setOnCheckedChangeListener { _, isChecked ->
            val currentMode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(spinnerNotificationMode.selectedItemPosition)
            if (isChecked && !currentMode.isAudioNotificationEnabled()) {
                val audioIdx = com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name)
                spinnerNotificationMode.setSelection(audioIdx)
            } else if (!isChecked && currentMode.isAudioNotificationEnabled()) {
                val noneIdx = com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.NONE.name)
                spinnerNotificationMode.setSelection(noneIdx)
            }
        }
    }

    private fun loadDevice() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HeadphoneSettingsActivity).bluetoothDeviceDao()
            val device = if (targetMac.isNotEmpty()) {
                dao.getDevice(targetMac)
            } else {
                // Strictly look for headphones or generic bluetooth devices; never grab smart glasses
                dao.getConnectedDeviceByType(BluetoothDeviceType.HEADPHONES.name)
                    ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.HEADPHONES.name && it.isConnected }
                    ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.HEADPHONES.name }
                    ?: dao.getAllDevices().find { it.deviceType == BluetoothDeviceType.GENERIC.name }
            }

            if (device != null) {
                currentDevice = device
                targetMac = device.macAddress
                withContext(Dispatchers.Main) {
                    txtHeadphoneName.text = device.name
                    txtHeadphoneMac.text = device.macAddress
                    selectSpinnerAction(spinnerTap1, device.tap1Action, "MEDIA_PLAY_PAUSE")
                    selectSpinnerAction(spinnerTap2, device.tap2Action, "LAUNCH_GEMINI")
                    selectSpinnerAction(spinnerTap3, device.tap3Action, "LAUNCH_PHONE_ASSISTANT")
                    selectSpinnerAction(spinnerLongPress, device.longPressAction, "CREATE_AI_NOTE")
                    switchTts.isChecked = device.ttsEnabled
                    switchAutoReadNotifications.isChecked = device.isAudioNotificationEnabled()
                    switchActiveListening.isChecked = device.activeListeningEnabled

                    val notifMode = if (device.notificationMode.isNotEmpty()) {
                        device.notificationMode
                    } else if (device.autoReadNotifications) {
                        com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name
                    } else {
                        com.myvu.client.data.DeviceNotificationMode.NONE.name
                    }
                    val notifIdx = com.myvu.client.data.DeviceNotificationMode.getIndex(notifMode)
                    spinnerNotificationMode.setSelection(notifIdx)
                    val modeObj = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(notifIdx)
                    txtNotificationModeDescription.text = "${modeObj.displayName}: ${modeObj.description}"

                    val batt = device.batteryLevel?.takeIf { it in 0..100 }
                    txtHeadphoneBattery.text = if (batt != null) "$batt%" else "--"
                }
            } else {
                // Check if there is a bonded audio device in the system
                val bDev = try {
                    val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                    val adapter = bm?.adapter ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    adapter?.bondedDevices?.find {
                        val cls = it.bluetoothClass?.majorDeviceClass
                        cls == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO ||
                        (it.name ?: "").contains("buds", ignoreCase = true) ||
                        (it.name ?: "").contains("pods", ignoreCase = true) ||
                        (it.name ?: "").contains("head", ignoreCase = true)
                    }
                } catch (_: SecurityException) { null }

                val resolvedMac = if (targetMac.isNotEmpty()) targetMac else (bDev?.address ?: "HEADPHONES-DEFAULT")
                val resolvedName = bDev?.name ?: "Auriculares Bluetooth"
                targetMac = resolvedMac

                withContext(Dispatchers.Main) {
                    txtHeadphoneName.text = resolvedName
                    txtHeadphoneMac.text = resolvedMac
                    txtHeadphoneBattery.text = "--"
                    selectSpinnerAction(spinnerTap1, "", "MEDIA_PLAY_PAUSE")
                    selectSpinnerAction(spinnerTap2, "", "LAUNCH_GEMINI")
                    selectSpinnerAction(spinnerTap3, "", "LAUNCH_PHONE_ASSISTANT")
                    selectSpinnerAction(spinnerLongPress, "", "CREATE_AI_NOTE")
                    switchTts.isChecked = true
                    switchAutoReadNotifications.isChecked = true
                    switchActiveListening.isChecked = false
                    val audioIdx = com.myvu.client.data.DeviceNotificationMode.getIndex(com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name)
                    spinnerNotificationMode.setSelection(audioIdx)
                    val modeObj = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(audioIdx)
                    txtNotificationModeDescription.text = "${modeObj.displayName}: ${modeObj.description}"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.myvu.client.service.BluetoothDeviceManager.getInstance(this).refreshAllDeviceBatteries()
        loadDevice()
    }

    private fun selectSpinnerAction(spinner: Spinner, actionKey: String, defaultKey: String) {
        val index = CommonDeviceActions.getIndexForAction(actionKey.ifEmpty { defaultKey })
        spinner.setSelection(index)
    }

    private fun getSelectedAction(spinner: Spinner, defaultKey: String): String {
        val actionId = CommonDeviceActions.getActionId(spinner.selectedItemPosition)
        return actionId.ifEmpty { defaultKey }
    }

    private fun saveSettings() {
        val tap1 = getSelectedAction(spinnerTap1, "MEDIA_PLAY_PAUSE")
        val tap2 = getSelectedAction(spinnerTap2, "LAUNCH_GEMINI")
        val tap3 = getSelectedAction(spinnerTap3, "LAUNCH_PHONE_ASSISTANT")
        val longPress = getSelectedAction(spinnerLongPress, "CREATE_AI_NOTE")
        val tts = switchTts.isChecked
        val activeListening = switchActiveListening.isChecked
        val selectedNotifMode = com.myvu.client.data.DeviceNotificationMode.getModeByIndex(spinnerNotificationMode.selectedItemPosition).id
        val readNotifs = switchAutoReadNotifications.isChecked ||
                selectedNotifMode == com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY.name ||
                selectedNotifMode == com.myvu.client.data.DeviceNotificationMode.BOTH.name

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HeadphoneSettingsActivity).bluetoothDeviceDao()
            val resolvedMac = targetMac.ifBlank { "HEADPHONES-DEFAULT" }
            val existing = currentDevice ?: dao.getDevice(resolvedMac)
            val dev = existing ?: BluetoothDeviceEntity(
                macAddress = resolvedMac,
                name = txtHeadphoneName.text.toString().ifBlank { "Auriculares Bluetooth" },
                deviceType = BluetoothDeviceType.HEADPHONES.name
            )

            val updated = dev.copy(
                tap1Action = tap1,
                tap2Action = tap2,
                tap3Action = tap3,
                longPressAction = longPress,
                ttsEnabled = tts,
                autoReadNotifications = readNotifs,
                notificationMode = selectedNotifMode,
                activeListeningEnabled = activeListening
            )
            dao.insertOrUpdate(updated)
            currentDevice = updated
            com.myvu.client.service.BluetoothDeviceManager.getInstance(this@HeadphoneSettingsActivity).refreshActiveDevice()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@HeadphoneSettingsActivity, "Ajustes de auriculares guardados", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_MAC = "extra_mac"

        fun start(context: Context, mac: String? = null) {
            val intent = Intent(context, HeadphoneSettingsActivity::class.java).apply {
                if (!mac.isNullOrEmpty()) {
                    putExtra(EXTRA_MAC, mac)
                }
            }
            context.startActivity(intent)
        }
    }
}
