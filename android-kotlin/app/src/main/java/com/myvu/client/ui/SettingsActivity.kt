package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.myvu.client.R
import com.myvu.client.ai.AiClient
import com.myvu.client.ai.AiProvider
import com.myvu.client.ai.SttProvider
import com.myvu.client.ai.TtsProvider
import com.myvu.client.app.feature.TouchGestureManager
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.Prefs
import com.myvu.client.service.MyvuService

class SettingsActivity : AppCompatActivity() {

    private lateinit var layApiKey: TextInputLayout
    private lateinit var layModel: TextInputLayout
    private lateinit var layAiEndpoint: TextInputLayout
    private lateinit var laySttApiKey: TextInputLayout
    private lateinit var laySttEndpoint: TextInputLayout
    private lateinit var laySttModel: TextInputLayout
    private lateinit var layTtsEndpoint: TextInputLayout
    private lateinit var layTtsApiKey: TextInputLayout
    private lateinit var layTtsModel: TextInputLayout
    private lateinit var layTtsVoice: TextInputLayout

    private lateinit var txtApiKey: TextInputEditText
    private lateinit var txtModel: TextInputEditText
    private lateinit var txtAiEndpoint: TextInputEditText
    private lateinit var txtSttApiKey: TextInputEditText
    private lateinit var txtSttEndpoint: TextInputEditText
    private lateinit var txtSttModel: TextInputEditText
    private lateinit var txtTtsEndpoint: TextInputEditText
    private lateinit var txtTtsApiKey: TextInputEditText
    private lateinit var txtTtsModel: TextInputEditText
    private lateinit var txtTtsVoice: TextInputEditText
    private lateinit var txtSystemPrompt: TextInputEditText
    private var chkIgnoreSsl: MaterialSwitch? = null

    private lateinit var aiProvider: AiProvider
    private lateinit var sttProvider: SttProvider
    private lateinit var ttsProvider: TtsProvider
    private var bindingAi: Boolean = false
    private var bindingStt: Boolean = false
    private var bindingTts: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        bindViews()
        configureProviderSelectors()
        bindStoredValues()
        configurePersistence()
        configureButtons()
    }

    private fun bindViews() {
        layApiKey = findViewById(R.id.layApiKey)
        layModel = findViewById(R.id.layModel)
        layAiEndpoint = findViewById(R.id.layAiEndpoint)
        chkIgnoreSsl = findViewById(R.id.chkIgnoreSsl)
        laySttApiKey = findViewById(R.id.laySttApiKey)
        laySttEndpoint = findViewById(R.id.laySttEndpoint)
        laySttModel = findViewById(R.id.laySttModel)
        layTtsEndpoint = findViewById(R.id.layTtsEndpoint)
        layTtsApiKey = findViewById(R.id.layTtsApiKey)
        layTtsModel = findViewById(R.id.layTtsModel)
        layTtsVoice = findViewById(R.id.layTtsVoice)

        txtApiKey = findViewById(R.id.txtApiKey)
        txtModel = findViewById(R.id.txtModel)
        txtAiEndpoint = findViewById(R.id.txtAiEndpoint)
        txtSttApiKey = findViewById(R.id.txtSttApiKey)
        txtSttEndpoint = findViewById(R.id.txtSttEndpoint)
        txtSttModel = findViewById(R.id.txtSttModel)
        txtTtsEndpoint = findViewById(R.id.txtTtsEndpoint)
        txtTtsApiKey = findViewById(R.id.txtTtsApiKey)
        txtTtsModel = findViewById(R.id.txtTtsModel)
        txtTtsVoice = findViewById(R.id.txtTtsVoice)
        txtSystemPrompt = findViewById(R.id.txtSystemPrompt)
    }

    private val providerClickListener = View.OnClickListener { v ->
        aiProvider = aiProviderFor(v.id)
        Prefs.setAiProvider(this@SettingsActivity, aiProvider.id)
        bindAiFields()
    }

    private fun configureProviderSelectors() {
        aiProvider = AiProvider.fromId(Prefs.aiProvider(this))
        val buttonIds = intArrayOf(
            R.id.btnProviderAssistant, R.id.btnProviderGemini, R.id.btnProviderOpenai,
            R.id.btnProviderClaude, R.id.btnProviderGroq, R.id.btnProviderNvidia,
            R.id.btnProviderLocal
        )
        for (id in buttonIds) {
            findViewById<View?>(id)?.setOnClickListener(providerClickListener)
        }

        sttProvider = SttProvider.fromId(Prefs.sttProvider(this))
        val sttGroup: MaterialButtonToggleGroup = findViewById(R.id.btnSttProviderGroup)
        sttGroup.check(if (sttProvider == SttProvider.LOCAL) R.id.btnSttLocal else R.id.btnSttGroq)
        sttGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sttProvider = if (checkedId == R.id.btnSttLocal) SttProvider.LOCAL else SttProvider.GROQ
            Prefs.setSttProvider(this, sttProvider.id)
            bindSttFields()
        }

        ttsProvider = TtsProvider.fromId(Prefs.ttsProvider(this))
        val ttsGroup: MaterialButtonToggleGroup = findViewById(R.id.btnTtsProviderGroup)
        ttsGroup.check(if (ttsProvider == TtsProvider.HTTP) R.id.btnTtsHttp else R.id.btnTtsSystem)
        ttsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            ttsProvider = if (checkedId == R.id.btnTtsHttp) TtsProvider.HTTP else TtsProvider.SYSTEM
            Prefs.setTtsProvider(this, ttsProvider.id)
            bindTtsFields()
        }
    }

    private fun bindStoredValues() {
        bindAiFields()
        bindSttFields()
        bindTtsFields()
        txtSystemPrompt.setText(Prefs.systemPrompt(this))
    }

    private fun configurePersistence() {
        persist(txtApiKey) { value ->
            if (!bindingAi) Prefs.setAiApiKey(this, aiProvider.id, value)
        }
        persist(txtModel) { value ->
            if (!bindingAi) Prefs.setAiModel(this, aiProvider.id, value.trim())
        }
        persist(txtAiEndpoint) { value ->
            if (!bindingAi) Prefs.setAiEndpoint(this, aiProvider.id, value.trim())
        }
        persist(txtSttApiKey) { value ->
            if (!bindingStt) Prefs.setSttApiKey(this, sttProvider.id, value)
        }
        persist(txtSttEndpoint) { value ->
            if (!bindingStt) Prefs.setSttEndpoint(this, sttProvider.id, value.trim())
        }
        persist(txtSttModel) { value ->
            if (!bindingStt) Prefs.setSttModel(this, sttProvider.id, value.trim())
        }
        persist(txtTtsEndpoint) { value ->
            if (!bindingTts) Prefs.setTtsEndpoint(this, value.trim())
        }
        persist(txtTtsApiKey) { value ->
            if (!bindingTts) Prefs.setTtsApiKey(this, value)
        }
        persist(txtTtsModel) { value ->
            if (!bindingTts) Prefs.setTtsModel(this, value.trim())
        }
        persist(txtTtsVoice) { value ->
            if (!bindingTts) Prefs.setTtsVoice(this, value.trim())
        }
        persist(txtSystemPrompt) { value ->
            Prefs.setSystemPrompt(this, value)
        }
        chkIgnoreSsl?.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setIgnoreSsl(this, isChecked)
        }
    }

    private fun configureButtons() {
        val btnResetPrompt = findViewById<View>(R.id.btnResetSystemPrompt)
        btnResetPrompt?.setOnClickListener {
            txtSystemPrompt.setText(AiClient.DEFAULT_SYSTEM_PROMPT)
            Prefs.setSystemPrompt(this, AiClient.DEFAULT_SYSTEM_PROMPT)
        }
        wireWeather()
        wireMirror()
        wireGlassesSettings()
        wireLogging()
        wireTouchpad()
        findViewById<View>(R.id.btnPickApps).setOnClickListener {
            startActivity(Intent(this, NotificationAppsActivity::class.java))
        }
        val navigateToDashboard = {
            if (isTaskRoot) {
                val intent = Intent(this, ConnectActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
            finish()
        }
        findViewById<View>(R.id.btnSettingsBack).setOnClickListener { navigateToDashboard() }
        findViewById<View?>(R.id.btnSettingsDrawer)?.setOnClickListener { navigateToDashboard() }
    }

    private fun wireTouchpad() {
        val grp: MaterialButtonToggleGroup? = findViewById(R.id.btnTouchpadGroup)
        if (grp == null) return

        val action = Prefs.touchpadLongPressAction(this)
        var checkedId = R.id.btnTouchpadAi
        if (TouchGestureManager.ACTION_MEDIA_PLAY_PAUSE == action) {
            checkedId = R.id.btnTouchpadMedia
        } else if (TouchGestureManager.ACTION_WEATHER_SYNC == action) {
            checkedId = R.id.btnTouchpadWeather
        } else if (TouchGestureManager.ACTION_TOGGLE_MIRROR == action) {
            checkedId = R.id.btnTouchpadMirror
        }
        grp.check(checkedId)

        grp.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            var selectedAction = TouchGestureManager.ACTION_AI_ASSISTANT
            if (id == R.id.btnTouchpadMedia) {
                selectedAction = TouchGestureManager.ACTION_MEDIA_PLAY_PAUSE
            } else if (id == R.id.btnTouchpadWeather) {
                selectedAction = TouchGestureManager.ACTION_WEATHER_SYNC
            } else if (id == R.id.btnTouchpadMirror) {
                selectedAction = TouchGestureManager.ACTION_TOGGLE_MIRROR
            }
            Prefs.setTouchpadLongPressAction(this, selectedAction)
        }
    }

    private fun wireLogging() {
        val sw: MaterialSwitch? = findViewById(R.id.swLogging)
        if (sw != null) {
            sw.isChecked = Prefs.loggingEnabled(this)
            sw.setOnCheckedChangeListener { _, checked ->
                Prefs.setLoggingEnabled(this, checked)
            }
        }
    }

    private fun wireMirror() {
        val sw: MaterialSwitch? = findViewById(R.id.swMirror)
        if (sw != null) {
            sw.isChecked = Prefs.mirrorEnabled(this)
            sw.setOnCheckedChangeListener { _, checked ->
                Prefs.setMirrorEnabled(this, checked)
            }
        }

        val sliderNotifDuration: Slider? = findViewById(R.id.sliderNotifDuration)
        val lblNotifDuration: TextView? = findViewById(R.id.lblNotifDuration)
        val currentNotifDuration = GlassesConfig.getNotificationDuration(this)

        if (sliderNotifDuration != null) {
            sliderNotifDuration.value = currentNotifDuration.toFloat()
            lblNotifDuration?.text = "Notification display time: ${currentNotifDuration}s (Default: 5s)"
            sliderNotifDuration.addOnChangeListener { _, value, _ ->
                val valInt = value.toInt()
                GlassesConfig.setNotificationDuration(this, valInt)
                lblNotifDuration?.text = "Notification display time: ${valInt}s (Default: 5s)"
            }
        }
    }

    private fun wireGlassesSettings() {
        val sliderBrightness: Slider? = findViewById(R.id.sliderBrightness)
        val sliderVolume: Slider? = findViewById(R.id.sliderVolume)
        val lblBrightness: TextView? = findViewById(R.id.lblBrightness)
        val lblVolume: TextView? = findViewById(R.id.lblVolume)

        val currentBrightness = GlassesConfig.getBrightness(this)
        val currentVolume = GlassesConfig.getVolume(this)

        if (sliderBrightness != null) {
            sliderBrightness.value = currentBrightness.toFloat()
            lblBrightness?.text = "Display brightness: $currentBrightness (Default: 3)"
            sliderBrightness.addOnChangeListener { _, value, _ ->
                val valInt = value.toInt()
                GlassesConfig.setBrightness(this, valInt)
                lblBrightness?.text = "Display brightness: $valInt (Default: 3)"
            }
        }

        if (sliderVolume != null) {
            sliderVolume.value = currentVolume.toFloat()
            lblVolume?.text = "Glasses volume: $currentVolume (Default: 11)"
            sliderVolume.addOnChangeListener { _, value, _ ->
                val valInt = value.toInt()
                GlassesConfig.setVolume(this, valInt)
                lblVolume?.text = "Glasses volume: $valInt (Default: 11)"
            }
        }

        val sliderStandbyPos: Slider? = findViewById(R.id.sliderStandbyPos)
        val lblStandbyPos: TextView? = findViewById(R.id.lblStandbyPos)
        val currentStandbyPos = GlassesConfig.getStandbyPosition(this)

        if (sliderStandbyPos != null) {
            sliderStandbyPos.value = currentStandbyPos.toFloat()
            lblStandbyPos?.text = "Widget FOV position: $currentStandbyPos (Default: 0)"
            sliderStandbyPos.addOnChangeListener { _, value, _ ->
                val valInt = value.toInt()
                GlassesConfig.setStandbyPosition(this, valInt)
                lblStandbyPos?.text = "Widget FOV position: $valInt (Default: 0)"
            }
        }

        val sliderScreenOff: Slider? = findViewById(R.id.sliderScreenOff)
        val lblScreenOff: TextView? = findViewById(R.id.lblScreenOff)
        val currentScreenOff = GlassesConfig.getScreenOffTime(this)

        if (sliderScreenOff != null) {
            sliderScreenOff.value = currentScreenOff.toFloat()
            lblScreenOff?.text = "Screen active time: ${currentScreenOff}s (Default: 10s)"
            sliderScreenOff.addOnChangeListener { _, value, _ ->
                val valInt = value.toInt()
                GlassesConfig.setScreenOffTime(this, valInt)
                lblScreenOff?.text = "Screen active time: ${valInt}s (Default: 10s)"
            }
        }
    }

    private fun bindAiFields() {
        bindingAi = true
        val local = aiProvider == AiProvider.LOCAL
        val assistant = aiProvider == AiProvider.ASSISTANT

        val selectedId = aiButtonFor(aiProvider)
        val buttonIds = intArrayOf(
            R.id.btnProviderAssistant, R.id.btnProviderGemini, R.id.btnProviderOpenai,
            R.id.btnProviderClaude, R.id.btnProviderGroq, R.id.btnProviderNvidia,
            R.id.btnProviderLocal
        )
        for (id in buttonIds) {
            val btn: MaterialButton? = findViewById(id)
            btn?.alpha = if (id == selectedId) 1.0f else 0.45f
        }

        layApiKey.visibility = if (assistant) View.GONE else View.VISIBLE
        layModel.visibility = if (assistant) View.GONE else View.VISIBLE
        layAiEndpoint.visibility = if (local) View.VISIBLE else View.GONE
        chkIgnoreSsl?.let {
            it.visibility = if (local) View.VISIBLE else View.GONE
            it.isChecked = Prefs.ignoreSsl(this)
        }

        layApiKey.hint = aiProvider.label + " API key"
        layApiKey.helperText = if (local) "Optional Bearer token" else "Create one at " + aiProvider.console
        layModel.helperText = if (local) "Required; use a model id exposed by the local server" else "Blank uses " + aiProvider.defaultModel
        txtApiKey.setText(Prefs.aiApiKey(this, aiProvider.id))
        txtModel.setText(Prefs.aiModel(this, aiProvider.id))
        txtAiEndpoint.setText(Prefs.aiEndpoint(this, aiProvider.id))
        bindingAi = false
    }

    private fun bindSttFields() {
        bindingStt = true
        val local = sttProvider == SttProvider.LOCAL
        laySttApiKey.hint = sttProvider.label + " API key"
        laySttApiKey.helperText = if (local) "Optional Bearer token" else "Create one at console.groq.com"
        laySttEndpoint.visibility = if (local) View.VISIBLE else View.GONE
        laySttModel.helperText = "Blank uses " + sttProvider.defaultModel
        txtSttApiKey.setText(Prefs.sttApiKey(this, sttProvider.id))
        txtSttEndpoint.setText(Prefs.sttEndpoint(this, sttProvider.id))
        txtSttModel.setText(Prefs.sttModel(this, sttProvider.id))
        bindingStt = false
    }

    private fun bindTtsFields() {
        bindingTts = true
        val http = ttsProvider == TtsProvider.HTTP
        val visibility = if (http) View.VISIBLE else View.GONE
        layTtsEndpoint.visibility = visibility
        layTtsApiKey.visibility = visibility
        layTtsModel.visibility = visibility
        layTtsVoice.visibility = visibility
        txtTtsEndpoint.setText(Prefs.ttsEndpoint(this))
        txtTtsApiKey.setText(Prefs.ttsApiKey(this))
        txtTtsModel.setText(Prefs.ttsModel(this))
        txtTtsVoice.setText(Prefs.ttsVoice(this))
        bindingTts = false
    }

    override fun onResume() {
        super.onResume()
        val count = Prefs.allowedPackages(this).size
        findViewById<TextView>(R.id.txtAllowedSummary).text = if (count == 0) {
            "No apps selected — nothing is mirrored"
        } else {
            "$count app${if (count == 1) "" else "s"} selected"
        }
    }

    private fun wireWeather() {
        val sw: MaterialSwitch = findViewById(R.id.swWeather)
        val place: TextInputEditText = findViewById(R.id.txtWeatherPlace)
        val sliderInterval: Slider? = findViewById(R.id.sliderWeatherInterval)
        val lblInterval: TextView? = findViewById(R.id.lblWeatherInterval)

        sw.isChecked = Prefs.weatherEnabled(this)
        place.setText(Prefs.weatherPlace(this))

        val currentInterval = Prefs.weatherIntervalMinutes(this)
        if (sliderInterval != null) {
            sliderInterval.value = currentInterval.toFloat()
            lblInterval?.text = "Sync interval: $currentInterval min (Default: 60 min)"
            sliderInterval.addOnChangeListener { _, value, _ ->
                val min = value.toInt()
                Prefs.setWeatherIntervalMinutes(this, min)
                lblInterval?.text = "Sync interval: $min min (Default: 60 min)"
            }
        }

        sw.setOnCheckedChangeListener { _, checked ->
            Prefs.setWeatherEnabled(this, checked)
            if (checked) syncWeatherNow(false)
        }
        persist(place) { v -> Prefs.setWeatherPlace(this, v) }
        findViewById<View>(R.id.btnSyncWeather).setOnClickListener { syncWeatherNow(true) }
    }

    private fun syncWeatherNow(announce: Boolean) {
        val c = MyvuService.activeConnection()
        if (c == null) {
            if (announce) {
                Toast.makeText(this, "Connect to the glasses first", Toast.LENGTH_SHORT).show()
            }
            return
        }
        c.syncWeatherNow()
        if (announce) Toast.makeText(this, "Syncing weather…", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private fun aiButtonFor(provider: AiProvider): Int {
            return when (provider) {
                AiProvider.OPENAI -> R.id.btnProviderOpenai
                AiProvider.GEMINI -> R.id.btnProviderGemini
                AiProvider.GROQ -> R.id.btnProviderGroq
                AiProvider.NVIDIA -> R.id.btnProviderNvidia
                AiProvider.ASSISTANT -> R.id.btnProviderAssistant
                AiProvider.LOCAL -> R.id.btnProviderLocal
                else -> R.id.btnProviderClaude
            }
        }

        private fun aiProviderFor(buttonId: Int): AiProvider {
            return when (buttonId) {
                R.id.btnProviderOpenai -> AiProvider.OPENAI
                R.id.btnProviderGemini -> AiProvider.GEMINI
                R.id.btnProviderGroq -> AiProvider.GROQ
                R.id.btnProviderNvidia -> AiProvider.NVIDIA
                R.id.btnProviderAssistant -> AiProvider.ASSISTANT
                R.id.btnProviderLocal -> AiProvider.LOCAL
                else -> AiProvider.CLAUDE
            }
        }

        private fun persist(field: TextView, saver: (String) -> Unit) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(text: Editable?) {
                    saver(text?.toString() ?: "")
                }
            })
        }
    }
}
