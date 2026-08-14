package com.myvu.client.ui

import android.Manifest
import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
import com.myvu.client.app.feature.NavCommands
import com.myvu.client.core.GlassesConfig
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.database.Note
import com.myvu.client.database.NoteRepository
import com.myvu.client.database.Reminder
import com.myvu.client.database.ReminderRepository
import com.myvu.client.service.ConnectionManager
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MirrorNotificationListener
import com.myvu.client.service.MyvuService
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class ConnectActivity : AppCompatActivity(), LogBus.Listener {

    private lateinit var txtMac: TextInputEditText
    private lateinit var txtNotifyTitle: TextInputEditText
    private lateinit var txtNotifyBody: TextInputEditText
    private lateinit var txtAsk: TextInputEditText
    private lateinit var txtTici: TextInputEditText
    private lateinit var txtDest: TextInputEditText
    private lateinit var txtStatus: TextView
    private lateinit var txtGlasses: TextView
    private lateinit var statusDot: View
    private lateinit var progress: View
    private lateinit var rvLog: RecyclerView
    private lateinit var logAdapter: LogAdapter

    private lateinit var pairingOverlay: View
    private lateinit var ring1: View
    private lateinit var ring2: View
    private lateinit var ring3: View
    private lateinit var pairButtons: View
    private lateinit var imgGlasses: ImageView
    private lateinit var imgCheck: ImageView
    private lateinit var pairTitle: TextView
    private lateinit var pairSubtitle: TextView
    private lateinit var btnPairDone: MaterialButton
    private val ringAnimators = ArrayList<Animator>()
    private var pairing: Boolean = false

    private var service: MyvuService? = null
    private var bound: Boolean = false
    private var lastDotColor: Int = 0

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = (binder as MyvuService.LocalBinder).getService()
            service = s
            bound = true
            val conn = s.connection()
            if (conn != null) {
                render(conn.state())
                if (conn.state() == ConnectionState.READY) {
                    conn.queryBatteryInfo()
                }
            } else {
                render(ConnectionState.IDLE)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        txtMac = findViewById(R.id.txtMac)
        txtNotifyTitle = findViewById(R.id.txtNotifyTitle)
        txtNotifyBody = findViewById(R.id.txtNotifyBody)
        txtAsk = findViewById(R.id.txtAsk)
        txtTici = findViewById(R.id.txtTici)
        txtDest = findViewById(R.id.txtDest)
        txtStatus = findViewById(R.id.txtStatus)
        txtGlasses = findViewById(R.id.txtGlasses)
        statusDot = findViewById(R.id.statusDot)
        progress = findViewById(R.id.progress)
        rvLog = findViewById(R.id.rvLog)
        logAdapter = LogAdapter(this)
        rvLog.layoutManager = LinearLayoutManager(this)
        rvLog.adapter = logAdapter
        lastDotColor = ContextCompat.getColor(this, R.color.state_idle)

        pairingOverlay = findViewById(R.id.pairingOverlay)
        ring1 = findViewById(R.id.ring1)
        ring2 = findViewById(R.id.ring2)
        ring3 = findViewById(R.id.ring3)
        imgGlasses = findViewById(R.id.imgGlasses)
        imgCheck = findViewById(R.id.imgCheck)
        pairTitle = findViewById(R.id.pairTitle)
        pairSubtitle = findViewById(R.id.pairSubtitle)
        pairButtons = findViewById(R.id.pairButtons)
        btnPairDone = findViewById(R.id.btnPairDone)
        wirePairing()

        txtMac.setText(Prefs.targetMac(this))

        wireTabs()
        wireConnection()
        wireFeatures()
        wireSettings()
        wireNavigationDrawer()
        animateEntrance()
        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        Prefs.loggingEnabled(this)
        logAdapter.setAll(LogBus.history())
        scrollToBottom()
        LogBus.addListener(this)
        bindService(Intent(this, MyvuService::class.java), serviceConnection, 0)

        findViewById<MaterialSwitch>(R.id.swMirror).isChecked =
            MirrorNotificationListener.isEnabled(this) && Prefs.mirrorEnabled(this)
        MirrorNotificationListener.requestRebindIfEnabled(this)
    }

    override fun onResume() {
        super.onResume()
        updateDashboardData()
    }

    override fun onStop() {
        super.onStop()
        LogBus.removeListener(this)
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun wireNavigationDrawer() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)
        val navigationView: NavigationView = findViewById(R.id.navigationView)
        val tabs: TabLayout = findViewById(R.id.tabs)

        findViewById<View>(R.id.btnNavigationDrawer)?.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    // Dashboard (current activity)
                }
                R.id.nav_notes -> {
                    startActivity(Intent(this, NotesActivity::class.java))
                }
                R.id.nav_ai_config -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_trackpad -> {
                    startActivity(Intent(this, TrackpadActivity::class.java))
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificationAppsActivity::class.java))
                }
                R.id.nav_logs -> {
                    tabs.getTabAt(1)?.select()
                }
            }
            true
        }
    }

    private fun wireTabs() {
        val pageControls: View = findViewById(R.id.pageControls)
        val pageLog: View = findViewById(R.id.pageLog)
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val log = tab.position == 1
                crossFade(if (log) pageLog else pageControls, if (log) pageControls else pageLog)
                if (log) scrollToBottom()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun crossFade(show: View, hide: View) {
        if (show.visibility == View.VISIBLE) return
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(180).start()
        hide.animate().alpha(0f).setDuration(120).withEndAction {
            hide.visibility = View.GONE
            hide.alpha = 1f
        }.start()
    }

    private fun wireConnection() {
        findViewById<View>(R.id.btnConnect).setOnClickListener { startConnection() }
        findViewById<View>(R.id.btnDisconnect).setOnClickListener { stopConnection() }
        findViewById<View>(R.id.btnTrackpad).setOnClickListener {
            startActivity(Intent(this, TrackpadActivity::class.java))
        }
        val openNotes = View.OnClickListener {
            startActivity(Intent(this, NotesActivity::class.java))
        }
        val openVoiceControl = View.OnClickListener {
            val intent = Intent(this, NotesActivity::class.java).apply {
                putExtra("AUTO_RECORD_VOICE", true)
            }
            startActivity(intent)
        }
        val openReminders = View.OnClickListener {
            val intent = Intent(this, NotesActivity::class.java).apply {
                putExtra("EXTRA_FILTER", "REMINDERS")
                putExtra("SHOW_REMINDERS", true)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnVoiceControl)?.setOnClickListener(openVoiceControl)
        findViewById<View>(R.id.btnNotes).setOnClickListener(openNotes)
        findViewById<View>(R.id.btnOpenAllNotes)?.setOnClickListener(openNotes)
        findViewById<View>(R.id.cardRecentNotes)?.setOnClickListener(openNotes)
        (findViewById<TextView>(R.id.txtRecentNote1Title)?.parent as? View)?.setOnClickListener(openNotes)
        (findViewById<TextView>(R.id.txtRecentNote2Title)?.parent as? View)?.setOnClickListener(openNotes)

        findViewById<View>(R.id.cardUpcomingReminders)?.setOnClickListener(openReminders)
        (findViewById<TextView>(R.id.txtReminder1Title)?.parent as? View)?.setOnClickListener(openReminders)
        (findViewById<TextView>(R.id.txtReminder2Title)?.parent as? View)?.setOnClickListener(openReminders)

        findViewById<View>(R.id.cardStatus).setOnClickListener {
            if (need()) {
                service?.connection()?.queryBatteryInfo()
                LogBus.log("refreshing battery info...")
            }
        }
        val openSettings = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener(openSettings)
        findViewById<View>(R.id.btnAiSettings).setOnClickListener(openSettings)
    }

    private fun wireFeatures() {
        findViewById<View>(R.id.btnNotify).setOnClickListener {
            if (!need()) return@setOnClickListener
            var title = text(txtNotifyTitle)
            var body = text(txtNotifyBody)
            if (body.isEmpty() && title.isEmpty()) {
                body = "Hello from the MYVU client"
            }
            service?.connection()?.sendTestNotification(
                if (title.isEmpty()) "Notification" else title, body
            )
        }

        findViewById<View>(R.id.btnAsk).setOnClickListener {
            if (!need()) return@setOnClickListener
            val q = text(txtAsk)
            if (q.isNotEmpty()) service?.connection()?.askAi(q)
        }

        findViewById<View>(R.id.btnTici).setOnClickListener {
            if (!need()) return@setOnClickListener
            val t = text(txtTici)
            service?.connection()?.openTeleprompter(
                if (t.isEmpty()) "Hello from the MYVU client." else t, "Prompter"
            )
        }

        val swMirror: MaterialSwitch = findViewById(R.id.swMirror)
        swMirror.setOnClickListener { toggleMirroring(it as MaterialSwitch) }

        findViewById<View>(R.id.btnNavStart).setOnClickListener {
            if (!need()) return@setOnClickListener
            val dest = text(txtDest)
            if (dest.isEmpty()) {
                try {
                    service?.connection()?.sendAction(
                        NavCommands.buildStart(
                            1, 1000, 1000, 120, "Demo Road",
                            300, "0", 0, 1, 0, 0, 0, false, false
                        ),
                        NavCommands.LAUNCH_TARGET_PKG, NavCommands.SOURCE_PKG
                    )
                } catch (e: Exception) {
                    LogBus.error("nav HUD failed", e)
                }
            } else {
                service?.connection()?.nav()?.start(dest)
            }
        }
        findViewById<View>(R.id.btnNavStop).setOnClickListener {
            if (need()) service?.connection()?.nav()?.stop()
        }
        findViewById<View>(R.id.btnIcNext).setOnClickListener {
            if (!need()) return@setOnClickListener
            calibrationIc = if (calibrationIc >= 16) 1 else calibrationIc + 1
            service?.connection()?.nav()?.sendCalibrationFrame(calibrationIc, "ic=$calibrationIc")
        }

        findViewById<View>(R.id.btnShareLog).setOnClickListener { shareLog() }
        findViewById<View>(R.id.btnClearLog).setOnClickListener {
            LogBus.clear()
            logAdapter.clear()
        }
    }

    private fun wireSettings() {
        wireSystemToggle(R.id.swWifi, Prefs.wifiEnabled(this), object : SystemToggleSetter {
            override fun set(enabled: Boolean) { Prefs.setWifiEnabled(this@ConnectActivity, enabled) }
            override fun sendToGlasses(conn: ConnectionManager, enabled: Boolean) { conn.toggleWifi(enabled) }
        })
        wireSystemToggle(R.id.swZen, Prefs.zenModeEnabled(this), object : SystemToggleSetter {
            override fun set(enabled: Boolean) { Prefs.setZenModeEnabled(this@ConnectActivity, enabled) }
            override fun sendToGlasses(conn: ConnectionManager, enabled: Boolean) { conn.setZenMode(enabled) }
        })
        wireSystemToggle(R.id.swWear, Prefs.wearDetectionEnabled(this), object : SystemToggleSetter {
            override fun set(enabled: Boolean) { Prefs.setWearDetectionEnabled(this@ConnectActivity, enabled) }
            override fun sendToGlasses(conn: ConnectionManager, enabled: Boolean) { conn.setWearDetection(enabled) }
        })
        wireSystemToggle(R.id.swMusicTp, Prefs.musicTouchPanelEnabled(this), object : SystemToggleSetter {
            override fun set(enabled: Boolean) { Prefs.setMusicTouchPanelEnabled(this@ConnectActivity, enabled) }
            override fun sendToGlasses(conn: ConnectionManager, enabled: Boolean) { conn.setMusicTpControl(enabled) }
        })
        wireSystemToggle(R.id.swAir, Prefs.airModeEnabled(this), object : SystemToggleSetter {
            override fun set(enabled: Boolean) { Prefs.setAirModeEnabled(this@ConnectActivity, enabled) }
            override fun sendToGlasses(conn: ConnectionManager, enabled: Boolean) { conn.setAirMode(enabled) }
        })

        val sliderMainStandbyPos: com.google.android.material.slider.Slider? = findViewById(R.id.sliderMainStandbyPos)
        val lblMainStandbyPos: TextView? = findViewById(R.id.lblMainStandbyPos)
        val currentStandbyPos = GlassesConfig.getStandbyPosition(this)

        fun describePos(pos: Int): String = when (pos) {
            0 -> "Centro (0)"
            1 -> "Superior (1)"
            2 -> "Inferior (2)"
            3 -> "Lateral / Extremo (3)"
            else -> "Posición: $pos"
        }

        if (sliderMainStandbyPos != null) {
            sliderMainStandbyPos.value = currentStandbyPos.toFloat()
            lblMainStandbyPos?.text = "Posición del Dashboard en Gafas (FOV): ${describePos(currentStandbyPos)}"
            sliderMainStandbyPos.addOnChangeListener { _, value, fromUser ->
                val valInt = value.toInt()
                lblMainStandbyPos?.text = "Posición del Dashboard en Gafas (FOV): ${describePos(valInt)}"
                if (fromUser) {
                    GlassesConfig.setStandbyPosition(this, valInt)
                    val conn = service?.connection()
                    if (bound && conn != null && conn.state() == ConnectionState.READY) {
                        conn.setStandbyPosition(valInt)
                    }
                }
            }
        }

        findViewById<View>(R.id.btnSyncTime).setOnClickListener {
            if (need()) service?.connection()?.syncTime()
        }
        findViewById<View>(R.id.btnDeviceInfo).setOnClickListener {
            if (need()) service?.connection()?.query("get_device_info")
        }
    }

    private interface SystemToggleSetter {
        fun set(enabled: Boolean)
        fun sendToGlasses(conn: ConnectionManager, enabled: Boolean)
    }

    private fun wireSystemToggle(id: Int, initialState: Boolean, setter: SystemToggleSetter) {
        val sw: MaterialSwitch = findViewById(id) ?: return
        sw.isChecked = initialState
        sw.setOnCheckedChangeListener { _, isChecked ->
            setter.set(isChecked)
            val conn = service?.connection()
            if (bound && conn != null && conn.state() == ConnectionState.READY) {
                setter.sendToGlasses(conn, isChecked)
            }
        }
    }

    private fun startConnection() {
        val mac = text(txtMac).uppercase()
        val auto = mac.isEmpty()
        if (!auto && !mac.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) {
            LogBus.warn("not a valid MAC address: $mac")
            return
        }
        val start = Intent(this, MyvuService::class.java).setAction(MyvuService.ACTION_START)
        if (auto) {
            LogBus.log("no MAC entered -- auto-searching for glasses")
        } else {
            Prefs.setTargetMac(this, mac)
            start.putExtra(MyvuService.EXTRA_MAC, mac)
        }
        ContextCompat.startForegroundService(this, start)
        requestDozeExemption()
        if (!bound) bindService(Intent(this, MyvuService::class.java), serviceConnection, 0)
        showPairing()
    }

    private fun wirePairing() {
        findViewById<View>(R.id.btnPairCancel).setOnClickListener {
            stopConnection()
            dismissPairing()
        }
        findViewById<View>(R.id.btnPairRetry).setOnClickListener { startConnection() }
        btnPairDone.setOnClickListener { dismissPairing() }
    }

    private fun showPairing() {
        pairing = true
        pairingOverlay.alpha = 0f
        pairingOverlay.visibility = View.VISIBLE
        pairingOverlay.animate().alpha(1f).setDuration(220).start()

        imgCheck.visibility = View.GONE
        pairButtons.visibility = View.GONE
        btnPairDone.visibility = View.GONE

        val conn = service?.connection()
        val current = if (bound && conn != null) conn.state() else ConnectionState.CONNECTING
        if (current == ConnectionState.READY) {
            updatePairing(ConnectionState.READY)
        } else {
            imgGlasses.alpha = 0.5f
            pairTitle.text = "Searching for your glasses"
            pairSubtitle.text = "Make sure they are powered on and nearby"
            startRings()
            updatePairing(current)
        }
    }

    private fun dismissPairing() {
        pairing = false
        stopRings()
        pairingOverlay.animate().alpha(0f).setDuration(180).withEndAction {
            pairingOverlay.visibility = View.GONE
        }.start()
    }

    private fun startRings() {
        stopRings()
        val rings = arrayOf(ring1, ring2, ring3)
        for (i in rings.indices) {
            val r = rings[i]
            val sx = PropertyValuesHolder.ofFloat("scaleX", 0.35f, 1f)
            val sy = PropertyValuesHolder.ofFloat("scaleY", 0.35f, 1f)
            val al = PropertyValuesHolder.ofFloat("alpha", 0.7f, 0f)
            val ping = ObjectAnimator.ofPropertyValuesHolder(r, sx, sy, al).apply {
                duration = 2000
                startDelay = i * 650L
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                start()
            }
            ringAnimators.add(ping)
        }
        val breathe = ObjectAnimator.ofFloat(imgGlasses, "alpha", 0.5f, 1f, 0.5f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ringAnimators.add(breathe)
    }

    private fun stopRings() {
        for (a in ringAnimators) a.cancel()
        ringAnimators.clear()
        for (r in arrayOf(ring1, ring2, ring3)) {
            r.scaleX = 0.35f
            r.scaleY = 0.35f
            r.alpha = 0f
        }
    }

    private fun updatePairing(state: ConnectionState) {
        if (!pairing) return
        when (state) {
            ConnectionState.CONNECTING, ConnectionState.BONDING -> {
                pairTitle.text = "Searching for your glasses"
                pairSubtitle.text = "Make sure they are powered on and nearby"
            }
            ConnectionState.PAIRING -> {
                pairTitle.text = "Found your glasses"
                pairSubtitle.text = deviceLabel()
                imgGlasses.animate().alpha(1f).scaleX(1.06f).scaleY(1.06f).setDuration(260)
                    .withEndAction {
                        imgGlasses.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    }.start()
            }
            ConnectionState.SESSION -> {
                pairTitle.text = "Almost ready"
                pairSubtitle.text = deviceLabel()
            }
            ConnectionState.READY -> pairingSuccess()
            ConnectionState.FAILED -> pairingFailed()
            else -> {}
        }
    }

    private fun pairingSuccess() {
        stopRings()
        imgGlasses.alpha = 1f
        pairTitle.text = "Connected"
        pairSubtitle.text = "Explore your AR world"
        imgCheck.visibility = View.VISIBLE
        imgCheck.scaleX = 0f
        imgCheck.scaleY = 0f
        imgCheck.animate().scaleX(1f).scaleY(1f).setDuration(360)
            .setInterpolator(OvershootInterpolator()).start()
        btnPairDone.visibility = View.VISIBLE
        pairingOverlay.postDelayed({ if (pairing) dismissPairing() }, 1600)
    }

    private fun pairingFailed() {
        stopRings()
        imgGlasses.alpha = 0.5f
        pairTitle.text = "Couldn't connect"
        pairSubtitle.text = "The glasses didn't respond. Check they are on, and that no other phone is connected to them."
        pairButtons.visibility = View.VISIBLE
    }

    private fun deviceLabel(): String {
        val conn = service?.connection()
        val info = conn?.glassesInfo()
        if (bound && info != null) {
            return info.name
        }
        return text(txtMac)
    }

    private fun stopConnection() {
        startService(Intent(this, MyvuService::class.java).setAction(MyvuService.ACTION_STOP))
        render(ConnectionState.IDLE)
    }

    private fun render(state: ConnectionState) {
        val busy = state == ConnectionState.BONDING || state == ConnectionState.CONNECTING
                || state == ConnectionState.PAIRING || state == ConnectionState.SESSION
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        txtStatus.text = describe(state)

        if (pairing) updatePairing(state)

        animateDot(dotColor(state))

        val conn = service?.connection()
        if (state == ConnectionState.READY && bound && conn?.glassesInfo() != null) {
            txtGlasses.text = conn.glassesInfo().toString()
            txtGlasses.visibility = View.VISIBLE
        } else if (state == ConnectionState.IDLE) {
            txtGlasses.visibility = View.GONE
        }
    }

    private fun dotColor(state: ConnectionState): Int {
        return when (state) {
            ConnectionState.READY -> ContextCompat.getColor(this, R.color.state_ready)
            ConnectionState.FAILED -> ContextCompat.getColor(this, R.color.state_failed)
            ConnectionState.IDLE -> ContextCompat.getColor(this, R.color.state_idle)
            else -> ContextCompat.getColor(this, R.color.state_connecting)
        }
    }

    private fun animateDot(target: Int) {
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), lastDotColor, target)
        anim.duration = 350
        anim.addUpdateListener { a ->
            val c = a.animatedValue as Int
            val bg = statusDot.background
            if (bg is GradientDrawable) {
                (bg.mutate() as GradientDrawable).setColor(c)
            } else {
                bg.mutate().setTint(c)
            }
        }
        anim.start()
        lastDotColor = target

        statusDot.clearAnimation()
        if (target == ContextCompat.getColor(this, R.color.state_ready)) {
            val pulse = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.4f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else {
            statusDot.alpha = 1f
        }
    }

    private fun animateEntrance() {
        val content: View = findViewById(R.id.content)
        content.post {
            if (content !is ViewGroup) return@post
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                child.alpha = 0f
                child.translationY = 40f
                child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * 45L).setDuration(320)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private var calibrationIc = 0

    private fun need(): Boolean {
        val conn = service?.connection()
        if (bound && conn?.state() == ConnectionState.READY) {
            return true
        }
        LogBus.warn("not connected yet")
        return false
    }

    private fun toggleMirroring(sw: MaterialSwitch) {
        if (!MirrorNotificationListener.isEnabled(this)) {
            sw.isChecked = false
            LogBus.log("grant notification access, then enable mirroring again")
            startActivity(MirrorNotificationListener.settingsIntent())
            return
        }
        Prefs.setMirrorEnabled(this, sw.isChecked)
        LogBus.log("notification mirroring " + if (sw.isChecked) "ON" else "OFF")
    }

    private fun requestDozeExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager?
        if (pm == null || pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            LogBus.trace("battery-optimisation prompt unavailable: $e")
        }
    }

    private fun shareLog() {
        try {
            val sb = StringBuilder()
            val history = LogBus.history()
            for (line in history) sb.append(line).append('\n')
            val fullLog = sb.toString()

            // Guardar en archivo para evitar el límite de tamaño de Intent.EXTRA_TEXT
            val logFile = java.io.File(cacheDir, "myvu_client_log.txt")
            logFile.writeText(fullLog)

            val logUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MYVU Client Log (${history.size} líneas)")
                putExtra(Intent.EXTRA_TEXT, fullLog)
                putExtra(Intent.EXTRA_STREAM, logUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Compartir Log Completo"))
        } catch (e: Exception) {
            LogBus.error("could not share log file", e)
            val fallbackText = LogBus.history().joinToString("\n")
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "MYVU client log")
                        .putExtra(Intent.EXTRA_TEXT, fallbackText),
                    "Share log"
                )
            )
        }
    }

    override fun onLine(line: String) {
        val atBottom = logAtBottom()
        val last = logAdapter.add(line)
        if (atBottom) rvLog.scrollToPosition(last)
        val conn = service?.connection()
        if (bound && conn != null) render(conn.state())
    }

    private fun logAtBottom(): Boolean = !rvLog.canScrollVertically(1)

    private fun scrollToBottom() {
        rvLog.post {
            val n = logAdapter.size()
            if (n > 0) rvLog.scrollToPosition(n - 1)
        }
    }

    private fun requestNeededPermissions() {
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(needed, Manifest.permission.BLUETOOTH_CONNECT)
            addIfMissing(needed, Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS)
        }
        addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION)
        addIfMissing(needed, Manifest.permission.RECORD_AUDIO)
        addIfMissing(needed, Manifest.permission.READ_CONTACTS)
        addIfMissing(needed, Manifest.permission.CALL_PHONE)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun addIfMissing(out: MutableList<String>, permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            out.add(permission)
        }
    }

    companion object {
        private const val REQ_PERMISSIONS = 1

        private fun describe(state: ConnectionState): String {
            return when (state) {
                ConnectionState.BONDING -> "Bonding…"
                ConnectionState.CONNECTING -> "Connecting…"
                ConnectionState.PAIRING -> "Exchanging keys…"
                ConnectionState.SESSION -> "Starting session…"
                ConnectionState.READY -> "Connected"
                ConnectionState.FAILED -> "Disconnected"
                else -> "Disconnected"
            }
        }

        private fun text(field: TextInputEditText): String {
            val c = field.text
            return c?.toString()?.trim() ?: ""
        }
    }

    private fun updateDashboardData() {
        val conn = service?.connection()
        val isConnected = bound && conn != null && conn.state() == ConnectionState.READY

        // Battery stat (no DB — safe on main thread)
        val txtBattery = findViewById<TextView>(R.id.txtBatteryStat)
        if (isConnected) {
            val batteryLevel = conn?.glassesInfo()?.battery ?: -1
            if (batteryLevel >= 0) txtBattery?.text = "$batteryLevel%"
            else { txtBattery?.text = "..."; conn?.queryBatteryInfo() }
        } else txtBattery?.text = "--"

        // Uptime stat (no DB — safe on main thread)
        val txtUptime = findViewById<TextView>(R.id.txtUptimeStat)
        if (isConnected) {
            val uptimeMs = conn?.connectedUptimeMs() ?: 0L
            val totalMinutes = uptimeMs / (1000 * 60)
            val h = totalMinutes / 60; val m = totalMinutes % 60
            txtUptime?.text = if (uptimeMs > 0) (if (h > 0) "${h}h ${m}m" else "${m}m") else "0m"
        } else txtUptime?.text = "--"

        // AI model stat (no DB — safe on main thread)
        val provider = Prefs.aiProvider(this)
        findViewById<TextView>(R.id.txtAiModelStat)?.text = when (provider.lowercase(Locale.ROOT)) {
            "gemini" -> "Google Gemini 1.5"
            "claude" -> "Anthropic Claude 3.5"
            "local" -> "AI Local / Ollama"
            else -> "OpenAI / GPT-4o"
        }

        // DB reads: off main thread
        lifecycleScope.launch {
            val recentNotes = withContext(Dispatchers.IO) {
                NoteRepository(this@ConnectActivity).getAllNotes().take(2)
            }
            val upcoming = withContext(Dispatchers.IO) {
                ReminderRepository(this@ConnectActivity).getPendingReminders()
                    .filter { it.triggerAt > System.currentTimeMillis() }.take(2)
            }
            populateRecentNotesWidget(recentNotes)
            populateUpcomingRemindersWidget(upcoming)
        }
    }

    private fun populateRecentNotesWidget(notes: List<Note>) {
        val txtTitle1 = findViewById<TextView>(R.id.txtRecentNote1Title) ?: return
        val txtBody1 = findViewById<TextView>(R.id.txtRecentNote1Body)
        val txtTitle2 = findViewById<TextView>(R.id.txtRecentNote2Title)
        val txtBody2 = findViewById<TextView>(R.id.txtRecentNote2Body)

        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        if (notes.isNotEmpty()) {
            val n1 = notes[0]
            txtTitle1.text = if (n1.title.isNotBlank()) n1.title else (if (n1.type == "VOICE") "Nota de voz" else "Nota sin título")
            val dateStr1 = if (n1.updatedAt > 0) sdf.format(Date(n1.updatedAt)) else ""
            txtBody1?.text = if (n1.body.isNotBlank()) {
                if (dateStr1.isNotEmpty()) "${n1.body} • $dateStr1" else n1.body
            } else {
                dateStr1.ifEmpty { "Sin contenido" }
            }

            if (notes.size > 1) {
                val n2 = notes[1]
                txtTitle2?.text = if (n2.title.isNotBlank()) n2.title else (if (n2.type == "VOICE") "Nota de voz" else "Nota sin título")
                val dateStr2 = if (n2.updatedAt > 0) sdf.format(Date(n2.updatedAt)) else ""
                txtBody2?.text = if (n2.body.isNotBlank()) {
                    if (dateStr2.isNotEmpty()) "${n2.body} • $dateStr2" else n2.body
                } else {
                    dateStr2.ifEmpty { "Sin contenido" }
                }
            } else {
                txtTitle2?.text = "--"
                txtBody2?.text = "Sin más notas recientes"
            }
        } else {
            txtTitle1.text = "Sin notas recientes"
            txtBody1?.text = "Sin contenido reciente"
            txtTitle2?.text = "--"
            txtBody2?.text = "--"
        }
    }

    private fun populateUpcomingRemindersWidget(reminders: List<Reminder>) {
        val txtTitle1 = findViewById<TextView>(R.id.txtReminder1Title) ?: return
        val txtTime1 = findViewById<TextView>(R.id.txtReminder1Time)
        val txtTitle2 = findViewById<TextView>(R.id.txtReminder2Title)
        val txtTime2 = findViewById<TextView>(R.id.txtReminder2Time)

        if (reminders.isNotEmpty()) {
            val r1 = reminders[0]
            txtTitle1.text = if (r1.title.isNotBlank()) r1.title else r1.body.ifBlank { "Recordatorio" }
            txtTime1?.text = formatTriggerTime(r1.triggerAt)

            if (reminders.size > 1) {
                val r2 = reminders[1]
                txtTitle2?.text = if (r2.title.isNotBlank()) r2.title else r2.body.ifBlank { "Recordatorio" }
                txtTime2?.text = formatTriggerTime(r2.triggerAt)
            } else {
                txtTitle2?.text = "--"
                txtTime2?.text = "--:--"
            }
        } else {
            txtTitle1.text = "Sin recordatorios pendientes"
            txtTime1?.text = "--:--"
            txtTitle2?.text = "--"
            txtTime2?.text = "--:--"
        }
    }

    private fun formatTriggerTime(triggerAt: Long): String {
        val diffMs = triggerAt - System.currentTimeMillis()
        if (diffMs <= 0) return "Ahora"
        val diffMinutes = diffMs / (1000 * 60)
        val diffHours = diffMinutes / 60

        return when {
            diffMinutes < 60 -> "Vence en ${diffMinutes}m"
            diffHours < 24 -> "Vence en ${diffHours}h ${diffMinutes % 60}m"
            else -> {
                val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                sdf.format(Date(triggerAt))
            }
        }
    }
}
