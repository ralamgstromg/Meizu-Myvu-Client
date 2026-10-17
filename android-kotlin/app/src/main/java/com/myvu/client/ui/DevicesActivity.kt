package com.myvu.client.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myvu.client.R
import com.myvu.client.core.LogBus
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.databinding.ActivityDevicesBinding
import com.myvu.client.service.BluetoothDeviceManager
import com.myvu.client.service.MyvuService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One screen for any Bluetooth device: paired devices with connect/disconnect, and nearby devices to pair.
 * Glasses MYVU keep their own panel (ConnectActivity) and settings; everything else uses the audio profiles.
 */
class DevicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicesBinding
    private lateinit var mgr: BluetoothDeviceManager
    private val adapter = DevicesAdapter(
        onPrimaryAction = ::onPrimaryAction,
        onMenu = ::showDeviceMenu,
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { updateStatus() }

    private val enableBluetoothLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { updateStatus() }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
            when {
                state == BluetoothDevice.BOND_BONDED -> {
                    toast("Dispositivo emparejado")
                    mgr.syncPairedDevices()
                }
                state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING ->
                    toast("No se pudo emparejar el dispositivo")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mgr = BluetoothDeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvDevices.adapter = adapter
        binding.btnScan.setOnClickListener { onScanClicked() }
        binding.btnFixStatus.setOnClickListener { onFixStatusClicked() }

        lifecycleScope.launch {
            combine(mgr.getAllDevicesFlow(), mgr.unbondedDiscoveredDevices, mgr.isScanning) { paired, nearby, scanning ->
                Triple(paired, nearby, scanning)
            }.collect { (paired, nearby, scanning) -> render(paired, nearby, scanning) }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        updateStatus()
        if (missingPermissions().isEmpty() && bluetoothOn()) mgr.syncPairedDevices()
    }

    override fun onStop() {
        // Discovery drains the radio; do not keep it running when the user leaves the screen.
        if (mgr.isScanning.value) mgr.stopScanning()
        runCatching { unregisterReceiver(bondReceiver) }
        super.onStop()
    }

    private fun render(paired: List<BluetoothDeviceEntity>, nearby: List<BluetoothDeviceEntity>, scanning: Boolean) {
        val pairedMacs = paired.map { it.macAddress.uppercase() }.toSet()
        val others = nearby.filter { it.macAddress.uppercase() !in pairedMacs }
        val rows = buildList {
            add(Row.Header("Mis dispositivos"))
            if (paired.isEmpty()) add(Row.Empty("Aún no hay dispositivos emparejados"))
            paired.forEach { add(Row.Paired(it)) }
            add(Row.Header("Cercanos"))
            if (others.isEmpty()) {
                add(Row.Empty(if (scanning) "Buscando…" else "Pulsa «Buscar dispositivos»"))
            }
            others.forEach { add(Row.Nearby(it)) }
        }
        adapter.submitList(rows)
        binding.btnScan.text = if (scanning) "Detener búsqueda" else "Buscar dispositivos"
    }

    private fun updateStatus() {
        val missing = missingPermissions()
        val btOn = bluetoothOn()
        binding.btnFixStatus.visibility = View.VISIBLE
        when {
            missing.isNotEmpty() -> {
                binding.txtStatus.text = "Faltan permisos de Bluetooth para buscar y conectar dispositivos."
                binding.btnFixStatus.text = "Conceder permisos"
            }
            !btOn -> {
                binding.txtStatus.text = "Bluetooth está apagado."
                binding.btnFixStatus.text = "Encender Bluetooth"
            }
            else -> {
                binding.txtStatus.text = "Bluetooth activo."
                binding.btnFixStatus.visibility = View.GONE
            }
        }
        binding.btnScan.isEnabled = missing.isEmpty() && btOn
    }

    private fun onFixStatusClicked() {
        val missing = missingPermissions()
        when {
            missing.isNotEmpty() -> permissionLauncher.launch(missing.toTypedArray())
            !bluetoothOn() -> enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun onScanClicked() {
        if (mgr.isScanning.value) {
            mgr.stopScanning()
        } else {
            mgr.startScanning()
            if (!mgr.isScanning.value) toast("No se pudo iniciar la búsqueda")
        }
    }

    private fun onPrimaryAction(row: Row) {
        when (row) {
            is Row.Paired -> {
                val d = row.device
                if (d.isConnected) disconnect(d) else connect(d)
            }
            is Row.Nearby -> {
                // Glasses bond over BLE + ECDH in ConnectionManager; a classic createBond() never completes for them.
                if (deviceType(row.device) == BluetoothDeviceType.SMART_GLASSES) {
                    connect(row.device)
                } else if (mgr.pairDevice(row.device.macAddress)) {
                    toast("Emparejando…")
                } else {
                    toast("No se pudo iniciar el emparejamiento")
                }
            }
            else -> Unit
        }
    }

    private fun connect(d: BluetoothDeviceEntity) {
        if (deviceType(d) == BluetoothDeviceType.SMART_GLASSES) {
            val intent = Intent(this, MyvuService::class.java)
                .setAction(MyvuService.ACTION_START)
                .putExtra(MyvuService.EXTRA_MAC, d.macAddress)
            ContextCompat.startForegroundService(this, intent)
            toast("Conectando con ${d.name}…")
        } else {
            lifecycleScope.launch {
                mgr.connectDeviceWithCleanSwitch(d) { ok ->
                    runOnUiThread { toast(if (ok) "Conectado a ${d.name}" else "No se pudo conectar a ${d.name}") }
                }
            }
        }
    }

    private fun disconnect(d: BluetoothDeviceEntity) {
        if (deviceType(d) == BluetoothDeviceType.SMART_GLASSES) {
            AlertDialog.Builder(this)
                .setTitle("Desconectar ${d.name}")
                .setMessage("Las gafas MYVU usan el servicio de la app: se detiene también la reconexión automática.")
                .setPositiveButton("Desconectar") { _, _ ->
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, MyvuService::class.java).setAction(MyvuService.ACTION_STOP)
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            mgr.disconnectAudioDevice(d.macAddress)
            toast("Desconectado de ${d.name}")
        }
    }

    private fun showDeviceMenu(d: BluetoothDeviceEntity, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Configurar")
        popup.menu.add(0, 2, 1, "Usar como principal")
        popup.menu.add(0, 3, 2, "Olvidar")
        if (deviceType(d) == BluetoothDeviceType.SMART_GLASSES) popup.menu.add(0, 4, 3, "Panel de gafas")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> configure(d)
                2 -> lifecycleScope.launch {
                    mgr.setPrimaryDevice(d.macAddress)
                    toast("${d.name} es el principal")
                }
                3 -> confirmForget(d)
                4 -> startActivity(Intent(this, ConnectActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun configure(d: BluetoothDeviceEntity) {
        when (deviceType(d)) {
            BluetoothDeviceType.SMART_GLASSES -> GlassesSettingsActivity.start(this, d.macAddress, d.name)
            BluetoothDeviceType.HEADPHONES -> HeadphoneSettingsActivity.start(this, d.macAddress)
            BluetoothDeviceType.GENERIC -> toast("Este dispositivo no tiene ajustes propios")
        }
    }

    private fun confirmForget(d: BluetoothDeviceEntity) {
        AlertDialog.Builder(this)
            .setTitle("Olvidar ${d.name}")
            .setMessage("Se quitará el emparejamiento con este dispositivo.")
            .setPositiveButton("Olvidar") { _, _ ->
                val removed = mgr.unpairDevice(d.macAddress)
                LogBus.log("DevicesActivity -> forget ${d.macAddress}: $removed")
                toast(if (removed) "Dispositivo olvidado" else "No se pudo olvidar el dispositivo")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun missingPermissions(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun bluetoothOn(): Boolean =
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun deviceType(d: BluetoothDeviceEntity): BluetoothDeviceType =
        runCatching { BluetoothDeviceType.valueOf(d.deviceType) }.getOrDefault(BluetoothDeviceType.GENERIC)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DevicesActivity::class.java))
        }
    }

    /** Rows of the list. Equality drives DiffUtil, so a row only rebinds when its data changes. */
    sealed interface Row {
        data class Header(val title: String) : Row
        data class Empty(val text: String) : Row
        data class Paired(val device: BluetoothDeviceEntity) : Row
        data class Nearby(val device: BluetoothDeviceEntity) : Row
    }

    private class DevicesAdapter(
        private val onPrimaryAction: (Row) -> Unit,
        private val onMenu: (BluetoothDeviceEntity, View) -> Unit,
    ) : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {

        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is Row.Header, is Row.Empty -> TYPE_TEXT
            is Row.Paired, is Row.Nearby -> TYPE_DEVICE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_TEXT) {
                TextHolder(inflater.inflate(R.layout.item_device_header, parent, false) as TextView)
            } else {
                DeviceHolder(inflater.inflate(R.layout.item_device_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is TextHolder -> holder.bind(getItem(position))
                is DeviceHolder -> holder.bind(getItem(position))
            }
        }

        private class TextHolder(private val view: TextView) : RecyclerView.ViewHolder(view) {
            fun bind(row: Row) {
                view.text = when (row) {
                    is Row.Header -> row.title
                    is Row.Empty -> row.text
                    else -> ""
                }
            }
        }

        private inner class DeviceHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.imgDeviceIcon)
            private val name: TextView = view.findViewById(R.id.txtDeviceName)
            private val sub: TextView = view.findViewById(R.id.txtDeviceSub)
            private val action: TextView = view.findViewById(R.id.txtDeviceAction)
            private val menu: View = view.findViewById(R.id.btnDeviceMenu)

            fun bind(row: Row) {
                val device = when (row) {
                    is Row.Paired -> row.device
                    is Row.Nearby -> row.device
                    else -> return
                }
                val type = runCatching { BluetoothDeviceType.valueOf(device.deviceType) }
                    .getOrDefault(BluetoothDeviceType.GENERIC)
                icon.setImageResource(
                    when (type) {
                        BluetoothDeviceType.SMART_GLASSES -> R.drawable.ic_glasses
                        BluetoothDeviceType.HEADPHONES -> R.drawable.ic_headphones
                        BluetoothDeviceType.GENERIC -> R.drawable.ic_bluetooth_device
                    }
                )
                name.text = device.name

                if (row is Row.Paired) {
                    val state = if (device.isConnected) "Conectado" else "Desconectado"
                    val battery = device.batteryLevel?.takeIf { it in 0..100 }?.let { " · $it%" } ?: ""
                    sub.text = state + battery
                    action.text = if (device.isConnected) "Desconectar" else "Conectar"
                    menu.visibility = View.VISIBLE
                    menu.setOnClickListener { onMenu(device, it) }
                } else {
                    sub.text = "Toca para emparejar"
                    action.text = "Emparejar"
                    menu.visibility = View.GONE
                    menu.setOnClickListener(null)
                }
                action.setOnClickListener { onPrimaryAction(row) }
                itemView.setOnClickListener { onPrimaryAction(row) }
            }
        }

        companion object {
            private const val TYPE_TEXT = 0
            private const val TYPE_DEVICE = 1
            private val DIFF = object : DiffUtil.ItemCallback<Row>() {
                override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean = when {
                    oldItem is Row.Paired && newItem is Row.Paired -> oldItem.device.macAddress == newItem.device.macAddress
                    oldItem is Row.Nearby && newItem is Row.Nearby -> oldItem.device.macAddress == newItem.device.macAddress
                    else -> oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean = oldItem == newItem
            }
        }
    }
}
