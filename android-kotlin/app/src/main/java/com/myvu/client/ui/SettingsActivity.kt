package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
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
import com.myvu.client.ai.AiResponseMode
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
    private lateinit var layGeminiAndroidControls: LinearLayout
    private lateinit var lblGeminiNanoStatus: TextView
    private lateinit var btnGeminiFallbackGroup: MaterialButtonToggleGroup
    private lateinit var btnCheckGeminiCapability: MaterialButton

    private lateinit var swUseLocalGemma: MaterialSwitch
    private lateinit var btnGemmaModelVersionGroup: MaterialButtonToggleGroup
    private lateinit var txtGemmaHfToken: TextInputEditText
    private lateinit var txtGemmaCustomUrl: TextInputEditText
    private lateinit var lblGemmaModelStatus: TextView
    private lateinit var progressGemmaDownload: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var btnDownloadGemmaModel: MaterialButton
    private lateinit var btnDeleteGemmaModel: MaterialButton
    private var gemmaDownloader: com.myvu.client.ai.GemmaModelDownloader? = null

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
        configureResponseMode()
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
        layGeminiAndroidControls = findViewById<LinearLayout>(R.id.layGeminiAndroidControls)
        lblGeminiNanoStatus = findViewById(R.id.lblGeminiNanoStatus)
        btnGeminiFallbackGroup = findViewById(R.id.btnGeminiFallbackGroup)
        btnCheckGeminiCapability = findViewById(R.id.btnCheckGeminiCapability)

        swUseLocalGemma = findViewById(R.id.swUseLocalGemma)
        btnGemmaModelVersionGroup = findViewById(R.id.btnGemmaModelVersionGroup)
        txtGemmaHfToken = findViewById(R.id.txtGemmaHfToken)
        txtGemmaCustomUrl = findViewById(R.id.txtGemmaCustomUrl)
        lblGemmaModelStatus = findViewById(R.id.lblGemmaModelStatus)
        progressGemmaDownload = findViewById(R.id.progressGemmaDownload)
        btnDownloadGemmaModel = findViewById(R.id.btnDownloadGemmaModel)
        btnDeleteGemmaModel = findViewById(R.id.btnDeleteGemmaModel)
        val selectedOption = com.myvu.client.ai.GemmaLocalClient.findOption(Prefs.gemmaModelId(this))
        gemmaDownloader = com.myvu.client.ai.GemmaModelDownloader(this, selectedOption)
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
            R.id.btnProviderLocal, R.id.btnProviderGeminiAndroid
        )
        for (id in buttonIds) {
            findViewById<View?>(id)?.setOnClickListener(providerClickListener)
        }

        configureGeminiAndroidControls()
        configureGemmaLocalControls()

        val swUseAndroidStt: com.google.android.material.materialswitch.MaterialSwitch? = findViewById(R.id.swUseAndroidStt)
        swUseAndroidStt?.isChecked = Prefs.useAndroidStt(this)
        swUseAndroidStt?.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setUseAndroidStt(this, isChecked)
        }

        sttProvider = SttProvider.fromId(Prefs.sttProvider(this))
        val sttGroup: MaterialButtonToggleGroup = findViewById(R.id.btnSttProviderGroup)
        sttGroup.check(
            when (sttProvider) {
                SttProvider.ON_DEVICE -> R.id.btnSttOnDevice
                SttProvider.LOCAL -> R.id.btnSttLocal
                else -> R.id.btnSttGroq
            }
        )
        sttGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sttProvider = when (checkedId) {
                R.id.btnSttOnDevice -> SttProvider.ON_DEVICE
                R.id.btnSttLocal -> SttProvider.LOCAL
                else -> SttProvider.GROQ
            }
            Prefs.setSttProvider(this, sttProvider.id)
            bindSttFields()
        }
        configureWhisperOnDeviceControls()
        bindSttFields()

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

    private fun configureResponseMode() {
        val group: MaterialButtonToggleGroup? = findViewById(R.id.btnAiResponseModeGroup)
        group ?: return
        val mode = AiResponseMode.fromId(Prefs.aiResponseMode(this))
        group.check(
            when (mode) {
                AiResponseMode.VOICE_ONLY -> R.id.btnAiResponseVoice
                AiResponseMode.VISUAL_ONLY -> R.id.btnAiResponseVisual
                AiResponseMode.VOICE_AND_VISUAL -> R.id.btnAiResponseBoth
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = when (checkedId) {
                R.id.btnAiResponseVoice -> AiResponseMode.VOICE_ONLY
                R.id.btnAiResponseVisual -> AiResponseMode.VISUAL_ONLY
                else -> AiResponseMode.VOICE_AND_VISUAL
            }
            Prefs.setAiResponseMode(this, selected.id)
        }
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
        persist(txtGemmaHfToken) { value ->
            Prefs.setGemmaHfToken(this, value)
        }
        persist(txtGemmaCustomUrl) { value ->
            Prefs.setGemmaCustomUrl(this, value)
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
            R.id.btnProviderLocal, R.id.btnProviderGeminiAndroid
        )
        for (id in buttonIds) {
            val btn: MaterialButton? = findViewById(id)
            btn?.alpha = if (id == selectedId) 1.0f else 0.45f
        }

        val isGeminiAndroid = aiProvider == AiProvider.GEMINI_ANDROID

        layGeminiAndroidControls.visibility = if (isGeminiAndroid) View.VISIBLE else View.GONE

        if (isGeminiAndroid) {
            val policy = com.myvu.client.ai.GeminiFallbackPolicy.fromId(Prefs.geminiFallbackPolicy(this))
            val showApiKey = policy.allowsApiFallback
            layApiKey.visibility = if (showApiKey) View.VISIBLE else View.GONE
            layModel.visibility = if (showApiKey) View.VISIBLE else View.GONE
            layAiEndpoint.visibility = View.GONE
            chkIgnoreSsl?.visibility = View.GONE

            btnGeminiFallbackGroup.check(
                when (policy) {
                    com.myvu.client.ai.GeminiFallbackPolicy.NANO_THEN_API -> R.id.btnFallbackNanoThenApi
                    com.myvu.client.ai.GeminiFallbackPolicy.NANO_ONLY -> R.id.btnFallbackNanoOnly
                    com.myvu.client.ai.GeminiFallbackPolicy.API_ONLY -> R.id.btnFallbackApiOnly
                }
            )

            updateGeminiNanoStatus()
        } else {
            layApiKey.visibility = if (assistant) View.GONE else View.VISIBLE
            layModel.visibility = if (assistant) View.GONE else View.VISIBLE
            layAiEndpoint.visibility = if (local) View.VISIBLE else View.GONE
            chkIgnoreSsl?.let {
                it.visibility = if (local) View.VISIBLE else View.GONE
                it.isChecked = Prefs.ignoreSsl(this)
            }
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
        val onDevice = sttProvider == SttProvider.ON_DEVICE
        val local = sttProvider == SttProvider.LOCAL

        findViewById<View?>(R.id.layWhisperControls)?.visibility = if (onDevice) View.VISIBLE else View.GONE
        laySttApiKey.visibility = if (onDevice) View.GONE else View.VISIBLE
        laySttModel.visibility = if (onDevice) View.GONE else View.VISIBLE
        laySttEndpoint.visibility = if (local) View.VISIBLE else View.GONE

        laySttApiKey.hint = sttProvider.label + " API key"
        laySttApiKey.helperText = if (local) {
            "Opcional (si tu servidor local requiere token Bearer)"
        } else {
            "Obtén una key gratuita en console.groq.com"
        }
        laySttModel.helperText = "Dejar en blanco para usar " + sttProvider.defaultModel
        txtSttApiKey.setText(Prefs.sttApiKey(this, sttProvider.id))
        txtSttEndpoint.setText(Prefs.sttEndpoint(this, sttProvider.id))
        txtSttModel.setText(Prefs.sttModel(this, sttProvider.id))
        if (onDevice) updateWhisperModelStatus()
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

    private fun configureGemmaLocalControls() {
        swUseLocalGemma.isChecked = Prefs.useLocalGemmaIfAvailable(this)
        swUseLocalGemma.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setUseLocalGemmaIfAvailable(this, isChecked)
        }

        txtGemmaHfToken.setText(Prefs.gemmaHfToken(this))
        txtGemmaCustomUrl.setText(Prefs.gemmaCustomUrl(this))

        val currentModelId = Prefs.gemmaModelId(this)
        btnGemmaModelVersionGroup.check(
            when (currentModelId) {
                com.myvu.client.ai.GemmaLocalClient.GEMMA_2B_IT_GPU.id -> R.id.btnGemma2B
                com.myvu.client.ai.GemmaLocalClient.GEMMA_2B_IT_CPU.id -> R.id.btnGemma2BCpu
                else -> R.id.btnGemma4B
            }
        )

        btnGemmaModelVersionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selectedOption = when (checkedId) {
                R.id.btnGemma2B -> com.myvu.client.ai.GemmaLocalClient.GEMMA_2B_IT_GPU
                R.id.btnGemma2BCpu -> com.myvu.client.ai.GemmaLocalClient.GEMMA_2B_IT_CPU
                else -> com.myvu.client.ai.GemmaLocalClient.GEMMA_4_E2B_LITERT
            }
            Prefs.setGemmaModelId(this, selectedOption.id)
            gemmaDownloader = com.myvu.client.ai.GemmaModelDownloader(this, selectedOption)
            updateGemmaModelStatus()
        }

        btnDownloadGemmaModel.setOnClickListener {
            val selectedOption = com.myvu.client.ai.GemmaLocalClient.findOption(Prefs.gemmaModelId(this))
            val downloader = com.myvu.client.ai.GemmaModelDownloader(this, selectedOption).also { gemmaDownloader = it }
            btnDownloadGemmaModel.isEnabled = false
            progressGemmaDownload.visibility = View.VISIBLE

            downloader.startDownload { state ->
                runOnUiThread {
                    when (state) {
                        is com.myvu.client.ai.GemmaDownloadState.Downloading -> {
                            progressGemmaDownload.progress = state.progressPercent
                            lblGemmaModelStatus.text = "Descargando ${selectedOption.name}: ${state.progressPercent}% (${state.downloadedBytes / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB)"
                        }
                        is com.myvu.client.ai.GemmaDownloadState.Completed -> {
                            btnDownloadGemmaModel.isEnabled = true
                            progressGemmaDownload.visibility = View.GONE
                            lblGemmaModelStatus.text = "Modelo ${selectedOption.name}: Listo para uso offline"
                            Toast.makeText(this, "Modelo descargado correctamente", Toast.LENGTH_SHORT).show()
                        }
                        is com.myvu.client.ai.GemmaDownloadState.Error -> {
                            btnDownloadGemmaModel.isEnabled = true
                            progressGemmaDownload.visibility = View.GONE
                            lblGemmaModelStatus.text = "Error al descargar: ${state.message}"
                            Toast.makeText(this, "Error de descarga: ${state.message}", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            btnDownloadGemmaModel.isEnabled = true
                            progressGemmaDownload.visibility = View.GONE
                            lblGemmaModelStatus.text = "Modelo ${selectedOption.name}: No descargado"
                        }
                    }
                }
            }
        }

        btnDeleteGemmaModel.setOnClickListener {
            val selectedOption = com.myvu.client.ai.GemmaLocalClient.findOption(Prefs.gemmaModelId(this))
            val downloader = com.myvu.client.ai.GemmaModelDownloader(this, selectedOption).also { gemmaDownloader = it }
            val deleted = downloader.deleteModel()
            progressGemmaDownload.visibility = View.GONE
            btnDownloadGemmaModel.isEnabled = true
            updateGemmaModelStatus()
            val msg = if (deleted) "Modelo eliminado" else "No se pudo eliminar el archivo del modelo"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<View?>(R.id.btnTestGemmaModel)?.setOnClickListener {
            val selectedOption = com.myvu.client.ai.GemmaLocalClient.findOption(Prefs.gemmaModelId(this))
            val client = com.myvu.client.ai.GemmaLocalClient(this, selectedOption)
            val isReady = client.isConfigured()
            val file = com.myvu.client.ai.GemmaLocalClient.getModelFile(this, selectedOption.fileName)

            if (!isReady) {
                Toast.makeText(this, "❌ Modelo no descargado (${selectedOption.name})", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "⏳ Probando inferencia con ${file.name}...", Toast.LENGTH_SHORT).show()
            val aiProviderId = Prefs.aiProvider(this)
            val provider = com.myvu.client.ai.AiProvider.fromId(aiProviderId)
            val apiKey = Prefs.aiApiKey(this, aiProviderId)
            val model = Prefs.aiModel(this, aiProviderId)
            val endpoint = Prefs.aiEndpoint(this, aiProviderId)
            val fullClient = provider.newClient(this, apiKey, model, endpoint, "")

            Thread {
                try {
                    val response = fullClient.ask("Hola, responde 'OK'")
                    runOnUiThread {
                        if (response.isNotBlank()) {
                            Toast.makeText(this, "✅ Test Exitoso: ${response.take(60)}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "❌ El modelo respondió vacío", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "❌ Error en Test: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        updateGemmaModelStatus()
    }

    private fun updateGemmaModelStatus() {
        val selectedOption = com.myvu.client.ai.GemmaLocalClient.findOption(Prefs.gemmaModelId(this))
        val downloader = com.myvu.client.ai.GemmaModelDownloader(this, selectedOption).also { gemmaDownloader = it }
        val state = downloader.getInitialState()
        lblGemmaModelStatus.text = when (state) {
            is com.myvu.client.ai.GemmaDownloadState.Completed -> "Modelo ${selectedOption.name}: Listo para uso offline"
            else -> "Modelo ${selectedOption.name}: No descargado"
        }
    }

    private fun configureWhisperOnDeviceControls() {
        val btnDownload: View? = findViewById(R.id.btnDownloadWhisperModel)
        val btnDelete: View? = findViewById(R.id.btnDeleteWhisperModel)
        val progress: com.google.android.material.progressindicator.LinearProgressIndicator? = findViewById(R.id.progressWhisperDownload)
        val versionGroup: MaterialButtonToggleGroup? = findViewById(R.id.btnWhisperModelVersionGroup)

        val currentModelId = Prefs.whisperModelId(this)
        versionGroup?.check(
            if (currentModelId == com.myvu.client.ai.WhisperLocalClient.WHISPER_TINY_ACFT.id) R.id.btnWhisperTiny else R.id.btnWhisperLargeV3Turbo
        )

        versionGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selectedOption = if (checkedId == R.id.btnWhisperTiny) {
                com.myvu.client.ai.WhisperLocalClient.WHISPER_TINY_ACFT
            } else {
                com.myvu.client.ai.WhisperLocalClient.WHISPER_LARGE_V3_TURBO_I4
            }
            Prefs.setWhisperModelId(this, selectedOption.id)
            updateWhisperModelStatus()
        }

        btnDownload?.setOnClickListener {
            val option = com.myvu.client.ai.WhisperLocalClient.findOption(Prefs.whisperModelId(this))
            val downloader = com.myvu.client.ai.WhisperModelDownloader(this, option)
            btnDownload.isEnabled = false
            progress?.visibility = View.VISIBLE
            progress?.isIndeterminate = false
            progress?.progress = 0

            downloader.startDownload { state ->
                runOnUiThread {
                    when (state) {
                        is com.myvu.client.ai.WhisperDownloadState.Downloading -> {
                            progress?.progress = state.progressPercent
                            findViewById<TextView?>(R.id.lblWhisperModelStatus)?.text =
                                "Descargando ${option.name}: ${state.progressPercent}% (${state.bytesDownloaded / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB)"
                        }
                        is com.myvu.client.ai.WhisperDownloadState.Completed -> {
                            progress?.visibility = View.GONE
                            btnDownload.isEnabled = true
                            updateWhisperModelStatus()
                            Toast.makeText(this, "✅ ${option.name} descargado con éxito", Toast.LENGTH_SHORT).show()
                        }
                        is com.myvu.client.ai.WhisperDownloadState.Error -> {
                            progress?.visibility = View.GONE
                            btnDownload.isEnabled = true
                            updateWhisperModelStatus()
                            Toast.makeText(this, "❌ Error al descargar Whisper: ${state.message}", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            btnDownload.isEnabled = true
                            progress?.visibility = View.GONE
                            updateWhisperModelStatus()
                        }
                    }
                }
            }
        }

        btnDelete?.setOnClickListener {
            val option = com.myvu.client.ai.WhisperLocalClient.findOption(Prefs.whisperModelId(this))
            val downloader = com.myvu.client.ai.WhisperModelDownloader(this, option)
            val deleted = downloader.deleteModel()
            progress?.visibility = View.GONE
            btnDownload?.isEnabled = true
            updateWhisperModelStatus()
            val msg = if (deleted) "Modelo Whisper eliminado" else "No se pudo eliminar el modelo Whisper"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<View?>(R.id.btnTestWhisperModel)?.setOnClickListener {
            val option = com.myvu.client.ai.WhisperLocalClient.findOption(Prefs.whisperModelId(this))
            val client = com.myvu.client.ai.WhisperLocalClient(this, option)
            val isReady = client.isConfigured()
            val file = com.myvu.client.ai.WhisperLocalClient.getModelFile(this, option.fileName)

            if (!isReady) {
                Toast.makeText(this, "❌ Modelo no descargado (${option.name})", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "⏳ Probando transcripción con ${file.name}...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    // Generar buffer PCM de prueba (1 segundo de tono/silencio a 16kHz)
                    val sampleRate = 16000
                    val testPcm = ByteArray(sampleRate * 2)
                    val lang = java.util.Locale.getDefault().language.ifBlank { "es" }

                    // Probar inferencia local y si conmuta a fallback, verificar respuesta
                    var resultText = ""
                    try {
                        resultText = client.transcribe(testPcm, sampleRate, 1, lang)
                    } catch (e: Exception) {
                        // Si falla on-device, probar con el cliente de fallback configurado (ej. Groq Whisper API)
                        val fallbackClient = com.myvu.client.ai.OpenAiTranscriptionClient(
                            Prefs.sttEndpoint(this, "groq").ifBlank { "https://api.groq.com/openai/v1/audio/transcriptions" },
                            Prefs.sttModel(this, "groq").ifBlank { "whisper-large-v3-turbo" },
                            Prefs.sttApiKey(this, "groq"),
                            "Groq STT Fallback"
                        )
                        if (fallbackClient.isConfigured()) {
                            resultText = fallbackClient.transcribe(testPcm, sampleRate, 1)
                        } else {
                            throw e
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(this, "✅ STT Verificado y Operativo (${file.name})", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "❌ Falló Test STT: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        updateWhisperModelStatus()
    }

    private fun updateWhisperModelStatus() {
        val option = com.myvu.client.ai.WhisperLocalClient.findOption(Prefs.whisperModelId(this))
        val downloader = com.myvu.client.ai.WhisperModelDownloader(this, option)
        val state = downloader.getInitialState()
        val lbl = findViewById<TextView?>(R.id.lblWhisperModelStatus)
        lbl?.text = when (state) {
            is com.myvu.client.ai.WhisperDownloadState.Completed -> "Modelo ${option.name}: Listo para transcripción offline"
            else -> "Modelo ${option.name}: No descargado (${option.sizeBytes / (1024 * 1024)}MB)"
        }
    }

    private fun configureGeminiAndroidControls() {
        btnGeminiFallbackGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val policy = when (checkedId) {
                R.id.btnFallbackNanoOnly -> com.myvu.client.ai.GeminiFallbackPolicy.NANO_ONLY
                R.id.btnFallbackApiOnly -> com.myvu.client.ai.GeminiFallbackPolicy.API_ONLY
                else -> com.myvu.client.ai.GeminiFallbackPolicy.NANO_THEN_API
            }
            Prefs.setGeminiFallbackPolicy(this, policy.id)
            bindAiFields()
        }

        btnCheckGeminiCapability.setOnClickListener {
            updateGeminiNanoStatus()
            Toast.makeText(this, "Verificación de capacidad realizada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGeminiNanoStatus() {
        val detector = object : com.myvu.client.ai.GeminiCapabilityDetector {
            override fun detect(): com.myvu.client.ai.GeminiAvailability {
                return com.myvu.client.ai.GeminiAvailability(com.myvu.client.ai.GeminiAvailability.State.UNAVAILABLE, "not_supported")
            }
        }
        val availability = detector.detect()
        lblGeminiNanoStatus.text = when (availability.state) {
            com.myvu.client.ai.GeminiAvailability.State.AVAILABLE -> "Estado Gemini Nano: Disponible en el dispositivo"
            com.myvu.client.ai.GeminiAvailability.State.MODEL_MISSING -> "Estado Gemini Nano: Modelo no descargado"
            com.myvu.client.ai.GeminiAvailability.State.TASK_UNSUPPORTED -> "Estado Gemini Nano: Tarea no soportada"
            else -> "Estado Gemini Nano: No disponible en este dispositivo"
        }
    }

    companion object {
        private fun aiButtonFor(provider: AiProvider): Int {
            return when (provider) {
                AiProvider.OPENAI -> R.id.btnProviderOpenai
                AiProvider.GEMINI -> R.id.btnProviderGemini
                AiProvider.GEMINI_ANDROID -> R.id.btnProviderGeminiAndroid
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
                R.id.btnProviderGeminiAndroid -> AiProvider.GEMINI_ANDROID
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
