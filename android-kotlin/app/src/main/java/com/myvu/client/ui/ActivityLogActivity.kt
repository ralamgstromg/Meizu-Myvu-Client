package com.myvu.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.myvu.client.R
import com.myvu.client.core.DeviceSource
import com.myvu.client.core.EdgeToEdgeHelper
import com.myvu.client.core.LogBus
import com.myvu.client.core.LogEntry
import com.myvu.client.core.Prefs
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityLogActivity : AppCompatActivity() {

    private lateinit var rvLogs: RecyclerView
    private lateinit var adapter: ActivityLogAdapter
    private lateinit var txtSubtitle: TextView
    private lateinit var swLogging: MaterialSwitch
    private lateinit var txtSwitchStatus: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var chipAutoScroll: Chip
    private lateinit var layoutEmpty: View
    private lateinit var txtEmptyTitle: TextView
    private lateinit var txtEmptySubtitle: TextView
    private lateinit var btnEmptyAction: View

    // Quick Diagnostic Metrics Pills
    private lateinit var pillMetricAll: View
    private lateinit var txtMetricAllTitle: TextView
    private lateinit var txtMetricAllCount: TextView
    private lateinit var pillMetricErrors: View
    private lateinit var txtMetricErrorsTitle: TextView
    private lateinit var txtMetricErrorsCount: TextView
    private lateinit var pillMetricWarnings: View
    private lateinit var txtMetricWarningsTitle: TextView
    private lateinit var txtMetricWarningsCount: TextView
    private lateinit var pillMetricGlasses: View
    private lateinit var txtMetricGlassesTitle: TextView
    private lateinit var txtMetricGlassesCount: TextView
    private lateinit var pillMetricBluetooth: View
    private lateinit var txtMetricBluetoothTitle: TextView
    private lateinit var txtMetricBluetoothCount: TextView

    private var allEntries = mutableListOf<LogEntry>()
    private var displayedEntries = mutableListOf<LogEntry>()

    private var selectedSource: DeviceSource = DeviceSource.ALL
    private var onlyErrors: Boolean = false
    private var onlyWarnings: Boolean = false
    private var searchQuery: String = ""
    private var autoScroll: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        initViews()
        EdgeToEdgeHelper.setupEdgeToEdge(
            activity = this,
            topBar = findViewById(R.id.topBarLog),
            scrollContent = rvLogs
        )
        setupRecyclerView()
        setupFilters()
        setupActions()
        loadInitialData()
        observeLogFlow()
    }

    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        txtSubtitle = findViewById(R.id.txtLogSubtitle)
        swLogging = findViewById(R.id.swActivityLogging)
        txtSwitchStatus = findViewById(R.id.txtLogSwitchStatus)
        etSearch = findViewById(R.id.etLogSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        chipGroupFilters = findViewById(R.id.chipGroupFilters)
        chipAutoScroll = findViewById(R.id.chipAutoScroll)
        layoutEmpty = findViewById(R.id.layoutEmptyLogs)
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle)
        txtEmptySubtitle = findViewById(R.id.txtEmptySubtitle)
        btnEmptyAction = findViewById(R.id.btnEmptyAction)
        rvLogs = findViewById(R.id.rvActivityLogs)

        // Diagnostic Metrics Views
        pillMetricAll = findViewById(R.id.pillMetricAll)
        txtMetricAllTitle = findViewById(R.id.txtMetricAllTitle)
        txtMetricAllCount = findViewById(R.id.txtMetricAllCount)

        pillMetricErrors = findViewById(R.id.pillMetricErrors)
        txtMetricErrorsTitle = findViewById(R.id.txtMetricErrorsTitle)
        txtMetricErrorsCount = findViewById(R.id.txtMetricErrorsCount)

        pillMetricWarnings = findViewById(R.id.pillMetricWarnings)
        txtMetricWarningsTitle = findViewById(R.id.txtMetricWarningsTitle)
        txtMetricWarningsCount = findViewById(R.id.txtMetricWarningsCount)

        pillMetricGlasses = findViewById(R.id.pillMetricGlasses)
        txtMetricGlassesTitle = findViewById(R.id.txtMetricGlassesTitle)
        txtMetricGlassesCount = findViewById(R.id.txtMetricGlassesCount)

        pillMetricBluetooth = findViewById(R.id.pillMetricBluetooth)
        txtMetricBluetoothTitle = findViewById(R.id.txtMetricBluetoothTitle)
        txtMetricBluetoothCount = findViewById(R.id.txtMetricBluetoothCount)

        val isLoggingEnabled = Prefs.loggingEnabled(this)
        swLogging.isChecked = isLoggingEnabled
        updateSwitchStatusText(isLoggingEnabled)

        swLogging.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setLoggingEnabled(this, isChecked)
            updateSwitchStatusText(isChecked)
            if (!isChecked) {
                allEntries.clear()
                applyFilters()
            }
        }

        btnEmptyAction.setOnClickListener {
            swLogging.isChecked = true
        }

        autoScroll = chipAutoScroll.isChecked
        chipAutoScroll.setOnCheckedChangeListener { _, isChecked ->
            autoScroll = isChecked
        }
    }

    private fun updateSwitchStatusText(enabled: Boolean) {
        if (enabled) {
            txtSwitchStatus.text = "Captura activa de eventos de todos los dispositivos"
            txtSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_secondary_label))
            btnEmptyAction.visibility = View.GONE
        } else {
            txtSwitchStatus.text = "Registro pausado: no se están capturando eventos"
            txtSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_orange))
            btnEmptyAction.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = ActivityLogAdapter(displayedEntries) { entry ->
            copyToClipboard(entry)
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvLogs.layoutManager = layoutManager
        rvLogs.adapter = adapter
    }

    private fun setupFilters() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val id = checkedIds.first()
            onlyErrors = (id == R.id.chipFilterErrors)
            onlyWarnings = false
            selectedSource = when (id) {
                R.id.chipFilterGlasses -> DeviceSource.GLASSES
                R.id.chipFilterBluetooth -> DeviceSource.BLUETOOTH
                R.id.chipFilterPhone -> DeviceSource.PHONE
                R.id.chipFilterAi -> DeviceSource.AI
                else -> DeviceSource.ALL
            }
            syncPillStates()
            applyFilters()
        }

        // Diagnostic Pill Click Handlers
        pillMetricAll.setOnClickListener {
            onlyErrors = false
            onlyWarnings = false
            selectedSource = DeviceSource.ALL
            chipGroupFilters.check(R.id.chipFilterAll)
            syncPillStates()
            applyFilters()
        }

        pillMetricErrors.setOnClickListener {
            onlyErrors = true
            onlyWarnings = false
            selectedSource = DeviceSource.ALL
            chipGroupFilters.check(R.id.chipFilterErrors)
            syncPillStates()
            applyFilters()
        }

        pillMetricWarnings.setOnClickListener {
            onlyErrors = false
            onlyWarnings = true
            selectedSource = DeviceSource.ALL
            chipGroupFilters.clearCheck()
            syncPillStates()
            applyFilters()
        }

        pillMetricGlasses.setOnClickListener {
            onlyErrors = false
            onlyWarnings = false
            selectedSource = DeviceSource.GLASSES
            chipGroupFilters.check(R.id.chipFilterGlasses)
            syncPillStates()
            applyFilters()
        }

        pillMetricBluetooth.setOnClickListener {
            onlyErrors = false
            onlyWarnings = false
            selectedSource = DeviceSource.BLUETOOTH
            chipGroupFilters.check(R.id.chipFilterBluetooth)
            syncPillStates()
            applyFilters()
        }
    }

    private fun syncPillStates() {
        val isAll = !onlyErrors && !onlyWarnings && selectedSource == DeviceSource.ALL
        pillMetricAll.setBackgroundResource(
            if (isAll) R.drawable.bg_ios_filter_pill_selected else R.drawable.bg_ios_filter_pill_unselected
        )
        txtMetricAllTitle.setTextColor(if (isAll) Color.WHITE else ContextCompat.getColor(this, R.color.ios_label))
        txtMetricAllCount.setTextColor(if (isAll) Color.WHITE else ContextCompat.getColor(this, R.color.ios_secondary_label))

        pillMetricErrors.setBackgroundResource(
            if (onlyErrors) R.drawable.bg_ios_filter_pill_selected else R.drawable.bg_ios_filter_pill_error_unselected
        )
        txtMetricErrorsTitle.setTextColor(if (onlyErrors) Color.WHITE else ContextCompat.getColor(this, R.color.ios_red))
        txtMetricErrorsCount.setTextColor(if (onlyErrors) Color.WHITE else ContextCompat.getColor(this, R.color.ios_red))

        pillMetricWarnings.setBackgroundResource(
            if (onlyWarnings) R.drawable.bg_ios_filter_pill_selected else R.drawable.bg_ios_filter_pill_unselected
        )
        txtMetricWarningsTitle.setTextColor(if (onlyWarnings) Color.WHITE else ContextCompat.getColor(this, R.color.ios_orange))
        txtMetricWarningsCount.setTextColor(if (onlyWarnings) Color.WHITE else ContextCompat.getColor(this, R.color.ios_orange))

        val isGlasses = !onlyErrors && !onlyWarnings && selectedSource == DeviceSource.GLASSES
        pillMetricGlasses.setBackgroundResource(
            if (isGlasses) R.drawable.bg_ios_filter_pill_selected else R.drawable.bg_ios_filter_pill_unselected
        )
        txtMetricGlassesTitle.setTextColor(if (isGlasses) Color.WHITE else ContextCompat.getColor(this, R.color.ios_label))
        txtMetricGlassesCount.setTextColor(if (isGlasses) Color.WHITE else ContextCompat.getColor(this, R.color.ios_secondary_label))

        val isBt = !onlyErrors && !onlyWarnings && selectedSource == DeviceSource.BLUETOOTH
        pillMetricBluetooth.setBackgroundResource(
            if (isBt) R.drawable.bg_ios_filter_pill_selected else R.drawable.bg_ios_filter_pill_unselected
        )
        txtMetricBluetoothTitle.setTextColor(if (isBt) Color.WHITE else ContextCompat.getColor(this, R.color.ios_label))
        txtMetricBluetoothCount.setTextColor(if (isBt) Color.WHITE else ContextCompat.getColor(this, R.color.ios_secondary_label))
    }

    private fun setupActions() {
        findViewById<View>(R.id.btnShareLogs).setOnClickListener { shareLogs() }
        findViewById<View>(R.id.btnClearLogs).setOnClickListener { confirmClearLogs() }
    }

    private fun loadInitialData() {
        allEntries.clear()
        allEntries.addAll(LogBus.entries())
        applyFilters()
    }

    private fun observeLogFlow() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LogBus.entryFlow.collect { entry ->
                    allEntries.add(entry)
                    if (matchesFilter(entry)) {
                        displayedEntries.add(entry)
                        adapter.notifyItemInserted(displayedEntries.size - 1)
                        if (autoScroll) {
                            rvLogs.scrollToPosition(displayedEntries.size - 1)
                        }
                    }
                    updateCountsAndEmptyState()
                }
            }
        }
    }

    private fun matchesFilter(entry: LogEntry): Boolean {
        if (onlyErrors && entry.level != Log.ERROR) {
            return false
        }
        if (onlyWarnings && entry.level != Log.WARN) {
            return false
        }
        if (selectedSource != DeviceSource.ALL && entry.source != selectedSource) {
            return false
        }
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase(Locale.ROOT)
            val inMsg = entry.message.lowercase(Locale.ROOT).contains(q)
            val inTag = entry.tag.lowercase(Locale.ROOT).contains(q)
            val inDevice = entry.deviceName?.lowercase(Locale.ROOT)?.contains(q) == true
            if (!inMsg && !inTag && !inDevice) {
                return false
            }
        }
        return true
    }

    private fun applyFilters() {
        displayedEntries.clear()
        for (entry in allEntries) {
            if (matchesFilter(entry)) {
                displayedEntries.add(entry)
            }
        }
        adapter.notifyDataSetChanged()
        updateCountsAndEmptyState()
        if (autoScroll && displayedEntries.isNotEmpty()) {
            rvLogs.scrollToPosition(displayedEntries.size - 1)
        }
    }

    private fun updateCountsAndEmptyState() {
        val total = allEntries.size
        val showing = displayedEntries.size

        val errorCount = allEntries.count { it.level == Log.ERROR }
        val warnCount = allEntries.count { it.level == Log.WARN }
        val glassesCount = allEntries.count { it.source == DeviceSource.GLASSES }
        val btCount = allEntries.count { it.source == DeviceSource.BLUETOOTH }

        txtMetricAllCount.text = total.toString()
        txtMetricErrorsCount.text = errorCount.toString()
        txtMetricWarningsCount.text = warnCount.toString()
        txtMetricGlassesCount.text = glassesCount.toString()
        txtMetricBluetoothCount.text = btCount.toString()

        txtSubtitle.text = if (total == showing) {
            "$total eventos registrados"
        } else {
            "Mostrando $showing de $total eventos"
        }

        if (displayedEntries.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvLogs.visibility = View.GONE
            if (!swLogging.isChecked) {
                txtEmptyTitle.text = "Registro de actividad pausado"
                txtEmptySubtitle.text = "El registro se encuentra desactivado. Actívalo para ver eventos en vivo."
            } else if (searchQuery.isNotEmpty() || selectedSource != DeviceSource.ALL || onlyErrors || onlyWarnings) {
                txtEmptyTitle.text = "Sin resultados"
                txtEmptySubtitle.text = "Ningún evento coincide con los filtros o término de búsqueda."
            } else {
                txtEmptyTitle.text = "Sin eventos registrados"
                txtEmptySubtitle.text = "Los eventos de la app y dispositivos conectados aparecerán aquí."
            }
        } else {
            layoutEmpty.visibility = View.GONE
            rvLogs.visibility = View.VISIBLE
        }
    }

    private fun confirmClearLogs() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Limpiar registros")
            .setMessage("¿Deseas vaciar todos los registros de actividad guardados en memoria?")
            .setPositiveButton("Limpiar") { _, _ ->
                LogBus.clear()
                allEntries.clear()
                applyFilters()
                Toast.makeText(this, "Registros limpiados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareLogs() {
        try {
            val listToShare = if (displayedEntries.isNotEmpty()) displayedEntries else allEntries
            if (listToShare.isEmpty()) {
                Toast.makeText(this, "No hay eventos para compartir", Toast.LENGTH_SHORT).show()
                return
            }

            val sb = StringBuilder()
            val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            sb.append("=== MYVU CLIENT - REGISTRO DE ACTIVIDAD UNIFICADO ===\n")
            sb.append("Generado: ").append(stampFormat.format(Date())).append("\n")
            sb.append("Total eventos: ").append(listToShare.size).append("\n\n")

            for (e in listToShare) {
                val timeStr = stampFormat.format(Date(e.timestamp))
                val levelStr = when (e.level) {
                    Log.ERROR -> "ERROR"
                    Log.WARN -> "WARN "
                    Log.DEBUG -> "DEBUG"
                    else -> "INFO "
                }
                sb.append("[$timeStr] [${e.source.name}] [$levelStr] [${e.tag}]")
                if (!e.deviceName.isNullOrBlank()) {
                    sb.append(" (${e.deviceName})")
                }
                sb.append(": ").append(e.message).append("\n")

                if (e.throwable != null) {
                    sb.append("   Throwable: ").append(android.util.Log.getStackTraceString(e.throwable)).append("\n")
                }
            }

            val fullText = sb.toString()
            val logFile = File(cacheDir, "myvu_activity_log.txt")
            logFile.writeText(fullText)

            val logUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MYVU Unified Activity Log (${listToShare.size} eventos)")
                putExtra(Intent.EXTRA_TEXT, fullText)
                putExtra(Intent.EXTRA_STREAM, logUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Compartir Registro de Actividad"))
        } catch (e: Exception) {
            LogBus.error("Error al compartir registros de actividad", e)
            val fallback = allEntries.joinToString("\n") { it.formattedLine }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "MYVU activity log")
                        .putExtra(Intent.EXTRA_TEXT, fallback),
                    "Compartir texto"
                )
            )
        }
    }

    private fun copyToClipboard(entry: LogEntry) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Log entry", entry.formattedLine.ifEmpty { entry.message })
        cm.setPrimaryClip(clip)
        Toast.makeText(this, "Evento copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Adapter for rendering unified LogEntry rows with Apple Inset Grouped design & visual error diagnosis.
 */
class ActivityLogAdapter(
    private val items: List<LogEntry>,
    private val onLongClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<ActivityLogAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val expandedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val isExpanded = expandedPositions.contains(position)
        holder.bind(entry, timeFormat, isExpanded, onToggleExpand = {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }, onLongClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardLogItem: MaterialCardView = itemView.findViewById(R.id.cardLogItem)
        private val imgLevelIcon: ImageView = itemView.findViewById(R.id.imgLevelIcon)
        private val badgeSource: LinearLayout = itemView.findViewById(R.id.badgeSource)
        private val imgSourceIcon: ImageView = itemView.findViewById(R.id.imgSourceIcon)
        private val txtSourceName: TextView = itemView.findViewById(R.id.txtSourceName)
        private val txtLogLevel: TextView = itemView.findViewById(R.id.txtLogLevel)
        private val txtTimestamp: TextView = itemView.findViewById(R.id.txtTimestamp)
        private val txtLogMessage: TextView = itemView.findViewById(R.id.txtLogMessage)
        private val layoutErrorDetails: LinearLayout = itemView.findViewById(R.id.layoutErrorDetails)
        private val btnToggleDetails: TextView = itemView.findViewById(R.id.btnToggleDetails)
        private val txtErrorDetail: TextView = itemView.findViewById(R.id.txtErrorDetail)

        fun bind(
            entry: LogEntry,
            timeFormat: SimpleDateFormat,
            isExpanded: Boolean,
            onToggleExpand: () -> Unit,
            onLongClick: (LogEntry) -> Unit
        ) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density

            // Timestamp
            txtTimestamp.text = timeFormat.format(Date(entry.timestamp))

            // Source Badge & Icon
            val (iconRes, sourceLabel, sourceColor, badgeBg) = when (entry.source) {
                DeviceSource.GLASSES -> Quadruple(
                    R.drawable.ic_glasses,
                    "Gafas MYVU",
                    ContextCompat.getColor(ctx, R.color.ios_blue),
                    R.drawable.bg_ios_badge_glasses
                )
                DeviceSource.BLUETOOTH -> Quadruple(
                    R.drawable.ic_bluetooth_device,
                    entry.deviceName ?: "Dispositivo BT",
                    ContextCompat.getColor(ctx, R.color.ios_purple),
                    R.drawable.bg_ios_badge_headphones
                )
                DeviceSource.AI -> Quadruple(
                    R.drawable.ic_sparkle,
                    "Asistente IA",
                    ContextCompat.getColor(ctx, R.color.ios_blue),
                    R.drawable.bg_ios_badge_info
                )
                DeviceSource.PHONE -> Quadruple(
                    R.drawable.ic_menu_hamburger,
                    "Teléfono / Sistema",
                    ContextCompat.getColor(ctx, R.color.ios_label),
                    R.drawable.bg_ios_badge_app
                )
                DeviceSource.ALL -> Quadruple(
                    R.drawable.ic_menu_hamburger,
                    "Sistema",
                    ContextCompat.getColor(ctx, R.color.ios_label),
                    R.drawable.bg_ios_badge_app
                )
            }

            badgeSource.setBackgroundResource(badgeBg)
            imgSourceIcon.setImageResource(iconRes)
            imgSourceIcon.setColorFilter(sourceColor)
            txtSourceName.text = sourceLabel
            txtSourceName.setTextColor(sourceColor)

            // Apple Visual Error / Status Highlighting
            when (entry.level) {
                Log.ERROR -> {
                    cardLogItem.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_red_tint))
                    cardLogItem.strokeColor = ContextCompat.getColor(ctx, R.color.ios_red)
                    cardLogItem.strokeWidth = (1.5f * density).toInt()

                    imgLevelIcon.visibility = View.VISIBLE
                    imgLevelIcon.setImageResource(R.drawable.ic_ios_error)

                    txtLogLevel.text = "ERROR"
                    txtLogLevel.setBackgroundResource(R.drawable.bg_ios_badge_error)
                    txtLogLevel.setTextColor(Color.WHITE)

                    txtLogMessage.setTextColor(ContextCompat.getColor(ctx, R.color.ios_red))
                }
                Log.WARN -> {
                    cardLogItem.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_orange_tint))
                    cardLogItem.strokeColor = ContextCompat.getColor(ctx, R.color.ios_orange)
                    cardLogItem.strokeWidth = (1.2f * density).toInt()

                    imgLevelIcon.visibility = View.VISIBLE
                    imgLevelIcon.setImageResource(R.drawable.ic_ios_warning)

                    txtLogLevel.text = "WARN"
                    txtLogLevel.setBackgroundResource(R.drawable.bg_ios_badge_warning)
                    txtLogLevel.setTextColor(Color.WHITE)

                    txtLogMessage.setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
                }
                Log.DEBUG -> {
                    cardLogItem.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_card_bg))
                    cardLogItem.strokeColor = ContextCompat.getColor(ctx, R.color.ios_separator)
                    cardLogItem.strokeWidth = (1f * density).toInt()

                    imgLevelIcon.visibility = View.GONE

                    txtLogLevel.text = "DEBUG"
                    txtLogLevel.setBackgroundResource(R.drawable.bg_ios_badge_app)
                    txtLogLevel.setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary_label))

                    txtLogMessage.setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary_label))
                }
                else -> {
                    cardLogItem.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_card_bg))
                    cardLogItem.strokeColor = ContextCompat.getColor(ctx, R.color.ios_separator)
                    cardLogItem.strokeWidth = (1f * density).toInt()

                    imgLevelIcon.visibility = View.GONE

                    txtLogLevel.text = "INFO"
                    txtLogLevel.setBackgroundResource(R.drawable.bg_ios_badge_info)
                    txtLogLevel.setTextColor(ContextCompat.getColor(ctx, R.color.ios_blue))

                    txtLogMessage.setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
                }
            }

            txtLogMessage.text = entry.message

            // Exception Collapsible Details
            if (entry.throwable != null) {
                layoutErrorDetails.visibility = View.VISIBLE
                val stack = android.util.Log.getStackTraceString(entry.throwable).ifBlank {
                    entry.throwable.message ?: entry.throwable.javaClass.simpleName
                }
                txtErrorDetail.text = stack

                if (isExpanded) {
                    txtErrorDetail.visibility = View.VISIBLE
                    btnToggleDetails.text = "Ocultar detalles del error ⌃"
                } else {
                    txtErrorDetail.visibility = View.GONE
                    btnToggleDetails.text = "Ver detalles del error ⌄"
                }

                btnToggleDetails.setOnClickListener {
                    onToggleExpand()
                }
            } else {
                layoutErrorDetails.visibility = View.GONE
            }

            itemView.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
