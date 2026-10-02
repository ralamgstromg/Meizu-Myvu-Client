package com.myvu.client.ui.home

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.myvu.client.R
import com.myvu.client.ai.ActionPolicy
import com.myvu.client.core.Prefs
import com.myvu.client.routines.RoutineAction
import com.myvu.client.routines.RoutineChannel
import com.myvu.client.routines.RoutineRunner
import com.myvu.client.routines.RoutineScheduler
import com.myvu.client.routines.RoutineStore
import com.myvu.client.routines.ScheduledRoutine
import com.myvu.client.ui.chat.ChatActivity
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

/** "Agente": open the chat and manage scheduled routines. */
class AgentFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_agent, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<View>(R.id.btnAgentChat).setOnClickListener { startActivity(Intent(ctx, ChatActivity::class.java)) }
        view.findViewById<View>(R.id.btnAgentVoice).setOnClickListener {
            startActivity(Intent(ctx, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_AUTO_START_STT, true))
        }
        view.findViewById<View>(R.id.btnAgentNewRoutine).setOnClickListener { chooseTemplate() }
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        view.findViewById<TextView>(R.id.txtAgentPolicy).text =
            if (Prefs.actionPolicy(requireContext()) == ActionPolicy.ALLOW_ALL)
                "⚠️ El agente envía mensajes y llama sin pedir confirmación (Ajustes > General)."
            else "🔒 El agente te pide confirmación antes de enviar mensajes o llamar."
        renderRoutines(view)
    }

    private fun renderRoutines(view: View) {
        val ctx = requireContext()
        val list: LinearLayout = view.findViewById(R.id.listAgentRoutines)
        list.removeAllViews()
        val routines = RoutineStore.all(ctx).sortedBy { it.hour * 60 + it.minute }
        view.findViewById<View>(R.id.txtAgentRoutinesEmpty).visibility = if (routines.isEmpty()) View.VISIBLE else View.GONE
        routines.forEach { routine ->
            val item = layoutInflater.inflate(R.layout.item_routine, list, false)
            item.findViewById<TextView>(R.id.txtRoutineName).text = routine.name
            item.findViewById<TextView>(R.id.txtRoutineSchedule).text = describeSchedule(routine)
            item.findViewById<TextView>(R.id.txtRoutineActions).text =
                RoutineAction.values().filter { it in routine.actions }.joinToString(", ") { it.label } +
                    " → " + routine.channels.joinToString(", ") { it.label.lowercase() }
            item.findViewById<MaterialSwitch>(R.id.swRoutineEnabled).apply {
                isChecked = routine.enabled
                setOnCheckedChangeListener { _, checked -> saveAndSchedule(routine.copy(enabled = checked), rerender = false) }
            }
            item.findViewById<MaterialButton>(R.id.btnRoutineRun).setOnClickListener { runNow(routine) }
            item.setOnClickListener { edit(routine) }
            item.setOnLongClickListener { confirmDelete(routine); true }
            list.addView(item)
        }
    }

    private fun runNow(routine: ScheduledRoutine) {
        val app = requireContext().applicationContext
        Toast.makeText(app, "Ejecutando «${routine.name}»…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch { RoutineRunner.run(app, routine) }
    }

    private fun chooseTemplate() {
        val templates = ScheduledRoutine.templates()
        val labels = templates.map { "${it.name} (${describeSchedule(it)})" } + "Rutina en blanco"
        AlertDialog.Builder(requireContext())
            .setTitle("Nueva rutina")
            .setItems(labels.toTypedArray()) { _, which ->
                val base = templates.getOrNull(which) ?: ScheduledRoutine(
                    "", "Mi rutina", 8, 0, ScheduledRoutine.WEEKDAYS, setOf(RoutineAction.AGENDA), setOf(RoutineChannel.NOTIFICATION)
                )
                edit(base.copy(id = UUID.randomUUID().toString()))
            }
            .show()
    }

    /** Editor built in code: name, time, days, actions and channels. */
    private fun edit(routine: ScheduledRoutine) {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val name = EditText(ctx).apply { setText(routine.name); hint = "Nombre" }
        root.addView(name)

        var hour = routine.hour
        var minute = routine.minute
        val timeButton = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun showTime() { timeButton.text = "Hora: %d:%02d".format(hour, minute) }
        showTime()
        timeButton.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m; showTime() }, hour, minute, false).show()
        }
        root.addView(timeButton)

        val dayOrder = listOf(
            Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "X", Calendar.THURSDAY to "J",
            Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D"
        )
        val days = ChipGroup(ctx)
        val dayChips = dayOrder.map { (day, label) ->
            Chip(ctx).apply { text = label; isCheckable = true; isChecked = day in routine.days }.also { days.addView(it) }
        }
        root.addView(days)

        root.addView(TextView(ctx).apply { text = "Incluir"; setPadding(0, pad / 2, 0, 0) })
        val actionBoxes = RoutineAction.values().associateWith { a ->
            CheckBox(ctx).apply { text = a.label; isChecked = a in routine.actions }.also { root.addView(it) }
        }
        root.addView(TextView(ctx).apply { text = "Entregar por"; setPadding(0, pad / 2, 0, 0) })
        val channelBoxes = RoutineChannel.values().associateWith { c ->
            CheckBox(ctx).apply { text = c.label; isChecked = c in routine.channels }.also { root.addView(it) }
        }

        AlertDialog.Builder(ctx)
            .setTitle("Rutina")
            .setView(android.widget.ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Guardar") { _, _ ->
                val updated = routine.copy(
                    name = name.text.toString().trim().ifBlank { "Rutina" },
                    hour = hour,
                    minute = minute,
                    days = dayOrder.filterIndexed { i, _ -> dayChips[i].isChecked }.map { it.first }.toSet(),
                    actions = actionBoxes.filterValues { it.isChecked }.keys,
                    channels = channelBoxes.filterValues { it.isChecked }.keys.ifEmpty { setOf(RoutineChannel.NOTIFICATION) }
                )
                if (updated.actions.isEmpty() || updated.days.isEmpty()) {
                    Toast.makeText(ctx, "Elige al menos un día y una sección.", Toast.LENGTH_LONG).show()
                } else {
                    saveAndSchedule(updated, rerender = true)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveAndSchedule(routine: ScheduledRoutine, rerender: Boolean) {
        val ctx = requireContext()
        RoutineStore.save(ctx, routine)
        RoutineScheduler.schedule(ctx, routine)
        if (rerender) view?.let { renderRoutines(it) }
    }

    private fun confirmDelete(routine: ScheduledRoutine) {
        AlertDialog.Builder(requireContext())
            .setTitle("Borrar rutina")
            .setMessage("¿Borrar «${routine.name}»?")
            .setPositiveButton("Borrar") { _, _ ->
                RoutineScheduler.cancel(requireContext(), routine)
                RoutineStore.delete(requireContext(), routine.id)
                view?.let { renderRoutines(it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        fun describeSchedule(r: ScheduledRoutine): String {
            val period = if (r.hour < 12) "a. m." else "p. m."
            val h12 = when { r.hour == 0 -> 12; r.hour > 12 -> r.hour - 12; else -> r.hour }
            val days = when (r.days) {
                ScheduledRoutine.EVERY_DAY -> "todos los días"
                ScheduledRoutine.WEEKDAYS -> "lunes a viernes"
                else -> listOf(
                    Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "X", Calendar.THURSDAY to "J",
                    Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D"
                ).filter { it.first in r.days }.joinToString(" ") { it.second }
            }
            return "%d:%02d %s · %s".format(h12, r.minute, period, days)
        }
    }
}
