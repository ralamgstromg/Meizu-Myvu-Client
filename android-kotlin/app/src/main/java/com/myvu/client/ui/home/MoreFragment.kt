package com.myvu.client.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.ui.ActivityLogActivity
import com.myvu.client.ui.ConnectActivity
import com.myvu.client.ui.GlassesSettingsActivity
import com.myvu.client.ui.HeadphoneSettingsActivity
import com.myvu.client.ui.NotificationAppsActivity
import com.myvu.client.ui.PermissionsActivity
import com.myvu.client.ui.SettingsActivity

/** "Ajustes": one place for devices, AI, voice, notifications, permissions, logs and backup. */
class MoreFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_more, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list: LinearLayout = view.findViewById(R.id.listMore)
        val entries = listOf(
            "🔗 Dispositivos y conexión" to ConnectActivity::class.java,
            "👓 Gafas: HUD, gestos y respuestas" to GlassesSettingsActivity::class.java,
            "🎧 Audífonos: toques y lectura" to HeadphoneSettingsActivity::class.java,
            "🧠 IA, voz, notificaciones y respaldo" to SettingsActivity::class.java,
            "🔔 Apps que notifican a las gafas" to NotificationAppsActivity::class.java,
            "🛡️ Permisos" to PermissionsActivity::class.java,
            "📋 Registro de actividad" to ActivityLogActivity::class.java
        )
        entries.forEach { (label, target) ->
            list.addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
                setOnClickListener { startActivity(Intent(requireContext(), target)) }
            })
        }
    }
}
