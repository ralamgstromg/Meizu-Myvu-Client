package com.myvu.client.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.myvu.client.R
import com.myvu.client.core.TextToSpeechHelper
import com.myvu.client.routines.RoutineAction
import com.myvu.client.routines.RoutineRunner
import com.myvu.client.service.BluetoothDeviceManager
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MyvuService
import com.myvu.client.ui.ConnectActivity
import com.myvu.client.ui.NotesActivity
import com.myvu.client.ui.VoiceRecorderActivity
import com.myvu.client.ui.chat.ChatActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "Hoy": greeting, device status, ask bar, today's briefing and quick actions. */
class TodayFragment : Fragment() {

    private var briefing: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        view.findViewById<TextView>(R.id.txtTodayGreeting).text = when (hour) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        view.findViewById<TextView>(R.id.txtTodayDate).text =
            SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "CO")).format(Date()).replaceFirstChar { it.uppercase() }

        view.findViewById<View>(R.id.cardTodayDevices).setOnClickListener { startActivity(Intent(ctx, ConnectActivity::class.java)) }
        observeDevices(view.findViewById(R.id.txtTodayDevices))

        val ask: TextInputEditText = view.findViewById(R.id.edtTodayAsk)
        ask.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val q = ask.text?.toString()?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    ask.setText("")
                    startActivity(Intent(ctx, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_INITIAL_QUERY, q))
                }
                true
            } else false
        }
        view.findViewById<View>(R.id.btnTodayMic).setOnClickListener {
            startActivity(Intent(ctx, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_AUTO_START_STT, true))
        }

        view.findViewById<View>(R.id.btnTodayRefresh).setOnClickListener { loadBriefing(view) }
        view.findViewById<View>(R.id.btnTodayListen).setOnClickListener {
            if (briefing.isNotBlank()) {
                TextToSpeechHelper.init(ctx)
                TextToSpeechHelper.speak(briefing, context = ctx)
            }
        }

        view.findViewById<View>(R.id.btnQuickMeeting).setOnClickListener { startActivity(Intent(ctx, VoiceRecorderActivity::class.java)) }
        view.findViewById<View>(R.id.btnQuickNote).setOnClickListener { startActivity(Intent(ctx, NotesActivity::class.java)) }
        view.findViewById<View>(R.id.btnQuickReminders).setOnClickListener {
            startActivity(Intent(ctx, NotesActivity::class.java).putExtra("SHOW_REMINDERS", true))
        }
        view.findViewById<View>(R.id.btnQuickRoutines).setOnClickListener { (activity as? HomeActivity)?.selectTab(R.id.navAgent) }

        loadBriefing(view)
    }

    private fun observeDevices(label: TextView) {
        val manager = BluetoothDeviceManager.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.activeDevice.collect { device ->
                    val glassesReady = MyvuService.activeConnection()?.state == ConnectionState.READY
                    label.text = when {
                        device != null -> buildString {
                            append("🔗 ").append(device.name)
                            device.batteryLevel?.takeIf { it in 0..100 }?.let { append(" · ").append(it).append(" %") }
                            if (glassesReady) append(" · Gafas listas")
                        }
                        glassesReady -> "🔗 Gafas conectadas"
                        else -> "Sin dispositivos conectados · Toca para conectar"
                    }
                }
            }
        }
    }

    /** Local sections first (fast), then TRM and weather (network) in a second line. */
    private fun loadBriefing(view: View) {
        val ctx = requireContext().applicationContext
        val progress: ProgressBar = view.findViewById(R.id.progressToday)
        val main: TextView = view.findViewById(R.id.txtTodayBriefing)
        val external: TextView = view.findViewById(R.id.txtTodayExternal)
        progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val local = async {
                RoutineRunner.compose(ctx, setOf(RoutineAction.AGENDA, RoutineAction.REMINDERS, RoutineAction.TASKS, RoutineAction.BIRTHDAYS))
            }
            val remote = async { RoutineRunner.compose(ctx, setOf(RoutineAction.TRM, RoutineAction.WEATHER)) }
            val localText = local.await()
            main.text = localText.replace(". ", ".\n")
            val remoteText = remote.await()
            external.text = remoteText
            briefing = "$localText $remoteText"
            progress.visibility = View.GONE
        }
    }
}
