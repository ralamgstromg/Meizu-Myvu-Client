package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.data.BluetoothDeviceEntity
import com.myvu.client.data.BluetoothDeviceType
import com.myvu.client.service.BluetoothDeviceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceManagementBottomSheet : BottomSheetDialogFragment() {

    private lateinit var rvDevicesList: RecyclerView
    private lateinit var rvDiscoveredList: RecyclerView
    private lateinit var txtDiscoveredHeader: TextView
    private lateinit var progressScanDevices: ProgressBar
    private lateinit var btnScanNewDevices: MaterialButton
    private lateinit var btnAdvancedGlasses: MaterialButton
    private lateinit var btnCloseSheet: ImageButton

    private val bondedAdapter = DeviceAdapter(
        isDiscoveredMode = false,
        onConnectOrDisconnect = { device ->
            val devManager = BluetoothDeviceManager.getInstance(requireContext())
            if (device.isConnected) {
                // Disconnect
                com.myvu.client.core.TotalDisconnectHelper.performTotalDisconnect(requireContext(), showToast = false) {
                    Toast.makeText(requireContext(), "Dispositivo desconectado: ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Connect with Clean Switch protocol
                Toast.makeText(requireContext(), "Conectando a ${device.name}...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    devManager.connectDeviceWithCleanSwitch(device) { success ->
                        if (success) {
                            Toast.makeText(requireContext(), "Conectado a ${device.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "No se pudo conectar a ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        },
        onSetPrimary = { device ->
            lifecycleScope.launch {
                BluetoothDeviceManager.getInstance(requireContext()).setPrimaryDevice(device.macAddress)
                Toast.makeText(requireContext(), "${device.name} establecido como Dispositivo Principal", Toast.LENGTH_SHORT).show()
            }
        },
        onConfigure = { device ->
            dismiss()
            when (device.deviceType) {
                BluetoothDeviceType.SMART_GLASSES.name -> {
                    GlassesSettingsActivity.start(requireContext(), device.macAddress, device.name)
                }
                else -> {
                    HeadphoneSettingsActivity.start(requireContext(), device.macAddress)
                }
            }
        },
        onUnpair = { device ->
            val devManager = BluetoothDeviceManager.getInstance(requireContext())
            devManager.unpairDevice(device.macAddress)
            Toast.makeText(requireContext(), "Dispositivo olvidado: ${device.name}", Toast.LENGTH_SHORT).show()
        }
    )

    private val discoveredAdapter = DeviceAdapter(
        isDiscoveredMode = true,
        onConnectOrDisconnect = { device ->
            // In discovered mode, this acts as "Pair"
            val devManager = BluetoothDeviceManager.getInstance(requireContext())
            Toast.makeText(requireContext(), "Iniciando emparejamiento con ${device.name}...", Toast.LENGTH_SHORT).show()
            devManager.pairDevice(device.macAddress)
        },
        onSetPrimary = {},
        onConfigure = {},
        onUnpair = {}
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvDevicesList = view.findViewById(R.id.rvDevicesList)
        rvDiscoveredList = view.findViewById(R.id.rvDiscoveredList)
        txtDiscoveredHeader = view.findViewById(R.id.txtDiscoveredHeader)
        progressScanDevices = view.findViewById(R.id.progressScanDevices)
        btnScanNewDevices = view.findViewById(R.id.btnScanNewDevices)
        btnAdvancedGlasses = view.findViewById(R.id.btnAdvancedGlasses)
        btnCloseSheet = view.findViewById(R.id.btnCloseSheet)

        rvDevicesList.layoutManager = LinearLayoutManager(requireContext())
        rvDevicesList.adapter = bondedAdapter

        rvDiscoveredList.layoutManager = LinearLayoutManager(requireContext())
        rvDiscoveredList.adapter = discoveredAdapter

        btnCloseSheet.setOnClickListener { dismiss() }

        val devManager = BluetoothDeviceManager.getInstance(requireContext())

        btnScanNewDevices.setOnClickListener {
            devManager.syncPairedDevices()
            devManager.startScanning()
            progressScanDevices.visibility = View.VISIBLE
        }

        btnAdvancedGlasses.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), ConnectActivity::class.java)
            startActivity(intent)
        }

        view.findViewById<MaterialButton>(R.id.btnTotalDisconnectAll)?.setOnClickListener {
            dismiss()
            com.myvu.client.core.TotalDisconnectHelper.performTotalDisconnect(requireContext())
        }

        devManager.syncPairedDevices()

        lifecycleScope.launch {
            devManager.getAllDevicesFlow().collectLatest { devices ->
                bondedAdapter.submitList(devices)
            }
        }

        lifecycleScope.launch {
            devManager.unbondedDiscoveredDevices.collectLatest { discovered ->
                discoveredAdapter.submitList(discovered)
                val hasDiscovered = discovered.isNotEmpty()
                txtDiscoveredHeader.visibility = if (hasDiscovered) View.VISIBLE else View.GONE
                rvDiscoveredList.visibility = if (hasDiscovered) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            devManager.isScanning.collectLatest { scanning ->
                progressScanDevices.visibility = if (scanning) View.VISIBLE else View.GONE
                btnScanNewDevices.isEnabled = !scanning
                btnScanNewDevices.text = if (scanning) "Buscando dispositivos..." else "+ Buscar Nuevos Dispositivos"
            }
        }
    }

    override fun onDestroyView() {
        context?.let { ctx ->
            BluetoothDeviceManager.getInstance(ctx).stopScanning()
        }
        super.onDestroyView()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        context?.let { ctx ->
            BluetoothDeviceManager.getInstance(ctx).stopScanning()
        }
        super.onDismiss(dialog)
    }

    private class DeviceAdapter(
        private val isDiscoveredMode: Boolean,
        private val onConnectOrDisconnect: (BluetoothDeviceEntity) -> Unit,
        private val onSetPrimary: (BluetoothDeviceEntity) -> Unit,
        private val onConfigure: (BluetoothDeviceEntity) -> Unit,
        private val onUnpair: (BluetoothDeviceEntity) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        private var items = listOf<BluetoothDeviceEntity>()

        fun submitList(newList: List<BluetoothDeviceEntity>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bluetooth_device, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], isDiscoveredMode, onConnectOrDisconnect, onSetPrimary, onConfigure, onUnpair)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgType: ImageView = itemView.findViewById(R.id.imgDeviceType)
            val txtName: TextView = itemView.findViewById(R.id.txtDeviceName)
            val txtPrimaryBadge: TextView = itemView.findViewById(R.id.txtPrimaryBadge)
            val txtStatus: TextView = itemView.findViewById(R.id.txtDeviceStatus)
            val txtMac: TextView = itemView.findViewById(R.id.txtDeviceMac)
            val btnConnect: MaterialButton = itemView.findViewById(R.id.btnConnectDevice)
            val btnMore: MaterialButton = itemView.findViewById(R.id.btnMoreOptions)

            fun bind(
                device: BluetoothDeviceEntity,
                isDiscovered: Boolean,
                onConnectOrDisconnect: (BluetoothDeviceEntity) -> Unit,
                onSetPrimary: (BluetoothDeviceEntity) -> Unit,
                onConfigure: (BluetoothDeviceEntity) -> Unit,
                onUnpair: (BluetoothDeviceEntity) -> Unit
            ) {
                txtName.text = device.name
                txtMac.text = device.macAddress

                when (device.deviceType) {
                    BluetoothDeviceType.SMART_GLASSES.name -> {
                        imgType.setImageResource(R.drawable.ic_glasses)
                    }
                    BluetoothDeviceType.HEADPHONES.name -> {
                        imgType.setImageResource(R.drawable.ic_headphones)
                    }
                    else -> {
                        imgType.setImageResource(R.drawable.ic_bluetooth_device)
                    }
                }

                if (isDiscovered) {
                    txtPrimaryBadge.visibility = View.GONE
                    txtStatus.text = "Disponible para emparejar"
                    txtStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.ios_secondary_label))
                    btnConnect.text = "Vincular"
                    btnConnect.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.ios_blue))
                    btnConnect.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    btnMore.visibility = View.GONE
                } else {
                    txtPrimaryBadge.visibility = if (device.isPrimary) View.VISIBLE else View.GONE
                    btnMore.visibility = View.VISIBLE

                    val notifMode = com.myvu.client.data.DeviceNotificationMode.fromId(device.notificationMode)
                    val notifBadge = when (notifMode) {
                        com.myvu.client.data.DeviceNotificationMode.BOTH -> "HUD + TTS"
                        com.myvu.client.data.DeviceNotificationMode.VISUAL_ONLY -> "HUD"
                        com.myvu.client.data.DeviceNotificationMode.AUDIO_ONLY -> "TTS"
                        com.myvu.client.data.DeviceNotificationMode.NONE -> "Mudo"
                    }

                    val batteryStr = if (device.batteryLevel != null && device.batteryLevel in 0..100) {
                        " • ${device.batteryLevel}%"
                    } else ""

                    if (device.isConnected) {
                        txtStatus.text = "● Conectado$batteryStr • Notif: $notifBadge"
                        txtStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.cyber_teal))
                        btnConnect.text = "Desconectar"
                        btnConnect.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.ios_card_secondary))
                        btnConnect.setTextColor(ContextCompat.getColor(itemView.context, R.color.ios_red))
                    } else {
                        txtStatus.text = "Vinculado$batteryStr • Notif: $notifBadge"
                        txtStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.on_surface_variant_obsidian))
                        btnConnect.text = "Conectar"
                        btnConnect.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.ios_blue))
                        btnConnect.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    }

                    btnMore.setOnClickListener { v ->
                        val popup = PopupMenu(v.context, v)
                        if (!device.isPrimary) {
                            popup.menu.add(0, 1, 0, "★ Establecer como Principal")
                        }
                        popup.menu.add(0, 2, 1, "⚙ Configurar Gestos y Notificaciones")
                        popup.menu.add(0, 3, 2, "🗑 Desvincular / Olvidar")
                        popup.setOnMenuItemClickListener { item: MenuItem ->
                            when (item.itemId) {
                                1 -> onSetPrimary(device)
                                2 -> onConfigure(device)
                                3 -> onUnpair(device)
                            }
                            true
                        }
                        popup.show()
                    }
                }

                btnConnect.setOnClickListener {
                    onConnectOrDisconnect(device)
                }
            }
        }
    }

    companion object {
        const val TAG = "DeviceManagementBottomSheet"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            val sheet = DeviceManagementBottomSheet()
            sheet.show(fragmentManager, TAG)
        }
    }
}
