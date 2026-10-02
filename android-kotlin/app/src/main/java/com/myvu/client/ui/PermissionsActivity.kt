package com.myvu.client.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.myvu.client.R
import com.myvu.client.core.ServiceKeepAliveHelper
import com.myvu.client.service.AutoSendAccessibilityService
import com.myvu.client.service.MirrorNotificationListener

/**
 * Permission center: every permission the agent can use, what it is for, whether
 * it is granted, and a button to grant it. Nothing here is required up front;
 * features ask for what they need and degrade gracefully when it is missing.
 */
class PermissionsActivity : AppCompatActivity() {

    private data class Entry(
        val title: String,
        val purpose: String,
        val isGranted: (Context) -> Boolean,
        val grant: () -> Unit,
        val essential: Boolean = false
    )

    private val requestRuntime = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { render() }
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@PermissionsActivity, R.color.ios_system_bg))
            fitsSystemWindows = true
            addView(list)
        })
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun runtime(vararg permissions: String): Pair<(Context) -> Boolean, () -> Unit> {
        val perms = permissions.toList()
        return { ctx: Context ->
            perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        } to { requestRuntime.launch(perms.toTypedArray()) }
    }

    private fun settings(action: String, withPackage: Boolean = false): () -> Unit = {
        val intent = Intent(action)
        if (withPackage) intent.data = Uri.parse("package:$packageName")
        runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun entries(): List<Entry> {
        val out = mutableListOf<Entry>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val (g, r) = runtime(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            out += Entry("Bluetooth", "Conectar las gafas y los audífonos.", g, r, essential = true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val (g, r) = runtime(Manifest.permission.POST_NOTIFICATIONS)
            out += Entry("Notificaciones", "Avisos de recordatorios y rutinas, y el servicio en segundo plano.", g, r, essential = true)
        }
        runtime(Manifest.permission.RECORD_AUDIO).let { (g, r) ->
            out += Entry("Micrófono", "Hablar con el agente y grabar reuniones.", g, r, essential = true)
        }
        runtime(Manifest.permission.READ_CALENDAR).let { (g, r) ->
            out += Entry("Calendario", "Leer tu agenda para los resúmenes del día.", g, r)
        }
        runtime(Manifest.permission.READ_CONTACTS).let { (g, r) ->
            out += Entry("Contactos", "Llamar o escribir a un contacto por su nombre y avisarte de cumpleaños.", g, r)
        }
        runtime(Manifest.permission.CALL_PHONE).let { (g, r) ->
            out += Entry("Llamadas", "Hacer llamadas cuando se lo pides al agente.", g, r)
        }
        runtime(Manifest.permission.SEND_SMS).let { (g, r) ->
            out += Entry("SMS", "Enviar SMS directamente, sin abrir otra app.", g, r)
        }
        runtime(Manifest.permission.ACCESS_FINE_LOCATION).let { (g, r) ->
            out += Entry("Ubicación", "Clima local y navegación en el HUD.", g, r)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (g, r) = runtime(Manifest.permission.ACTIVITY_RECOGNITION)
            out += Entry("Actividad física", "Resumen de salud (pasos).", g, r)
        }
        out += Entry(
            "Acceso a notificaciones",
            "Mostrar tus notificaciones en las gafas y resumir mensajes y correos sin leer.",
            { MirrorNotificationListener.isEnabled(it) },
            settings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        )
        out += Entry(
            "MYVU Auto-Send (accesibilidad)",
            "Opcional. Permite pulsar «Enviar» en WhatsApp/Telegram por ti. Android solo lo permite con este servicio. " +
                "Sin él, el mensaje queda escrito y te avisamos para que toques Enviar.",
            { AutoSendAccessibilityService.isAccessibilityServiceEnabled(it) },
            settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            out += Entry(
                "Alarmas exactas",
                "Que recordatorios y rutinas suenen a la hora exacta.",
                { (it.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() },
                settings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackage = true)
            )
        }
        out += Entry(
            "Sin restricción de batería",
            "Mantener la conexión con las gafas en segundo plano.",
            { ServiceKeepAliveHelper.isBatteryOptimizationIgnored(it) },
            { ServiceKeepAliveHelper.requestIgnoreBatteryOptimization(this) }
        )
        return out
    }

    private fun render() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "Permisos"
            textSize = 28f
            setTextColor(ContextCompat.getColor(context, R.color.ios_label))
        })
        list.addView(TextView(this).apply {
            text = "Concede solo lo que vayas a usar. Cada función te pedirá su permiso cuando la necesites."
            setTextColor(ContextCompat.getColor(context, R.color.ios_secondary_label))
        })
        val pad = (12 * resources.displayMetrics.density).toInt()
        for (e in entries()) {
            val granted = e.isGranted(this)
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = pad }
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
            body.addView(TextView(this).apply {
                text = (if (granted) "✅ " else "⚪ ") + e.title + if (e.essential) " · esencial" else ""
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.ios_label))
            })
            body.addView(TextView(this).apply {
                text = e.purpose
                setTextColor(ContextCompat.getColor(context, R.color.ios_secondary_label))
            })
            if (!granted) {
                body.addView(MaterialButton(this).apply {
                    text = "Conceder"
                    setOnClickListener { e.grant() }
                })
            }
            card.addView(body)
            list.addView(card)
        }
        list.addView(View(this).apply { minimumHeight = pad * 2 })
    }

    companion object {
        private const val PREFS = "permissions_center"
        private const val KEY_SHOWN = "shown_once"

        /** True the first time, when an essential permission is still missing. */
        fun shouldShowOnFirstLaunch(context: Context): Boolean {
            if (context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SHOWN, false)) return false
            val essential = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return essential.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        }
    }
}
