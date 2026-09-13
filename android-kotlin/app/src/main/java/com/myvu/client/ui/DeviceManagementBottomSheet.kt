package com.myvu.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
    private lateinit var progressScanDevices: ProgressBar
    private lateinit var btnScanNewDevices: MaterialButton
    private lateinit var btnAdvancedGlasses: MaterialButton
    private lateinit var btnCloseSheet: ImageButton

    private val deviceAdapter = DeviceAdapter { device ->
        // On configure device clicked
        dismiss()
        when (device.deviceType) {
            BluetoothDeviceType.SMART_GLASSES.name -> {
                GlassesSettingsActivity.start(requireContext(), device.macAddress, device.name)
            }
            else -> {
                HeadphoneSettingsActivity.start(requireContext(), device.macAddress)
            }
        }
    }

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
        progressScanDevices = view.findViewById(R.id.progressScanDevices)
        btnScanNewDevices = view.findViewById(R.id.btnScanNewDevices)
        btnAdvancedGlasses = view.findViewById(R.id.btnAdvancedGlasses)
        btnCloseSheet = view.findViewById(R.id.btnCloseSheet)

        rvDevicesList.layoutManager = LinearLayoutManager(requireContext())
        rvDevicesList.adapter = deviceAdapter

        btnCloseSheet.setOnClickListener { dismiss() }

        btnScanNewDevices.setOnClickListener {
            val devManager = BluetoothDeviceManager.getInstance(requireContext())
            devManager.syncPairedDevices()
            devManager.startScanning()
            progressScanDevices.visibility = View.VISIBLE
            view.postDelayed({
                if (isAdded) {
                    progressScanDevices.visibility = View.GONE
                }
                context?.let { ctx ->
                    BluetoothDeviceManager.getInstance(ctx).stopScanning()
                }
            }, 8000)
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

        val devManager = BluetoothDeviceManager.getInstance(requireContext())
        devManager.syncPairedDevices()

        lifecycleScope.launch {
            devManager.getAllDevicesFlow().collectLatest { devices ->
                deviceAdapter.submitList(devices)
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
        private val onConfigure: (BluetoothDeviceEntity) -> Unit
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
            holder.bind(items[position], onConfigure)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgType: ImageView = itemView.findViewById(R.id.imgDeviceType)
            val txtName: TextView = itemView.findViewById(R.id.txtDeviceName)
            val txtStatus: TextView = itemView.findViewById(R.id.txtDeviceStatus)
            val txtMac: TextView = itemView.findViewById(R.id.txtDeviceMac)
            val btnConfigure: MaterialButton = itemView.findViewById(R.id.btnConfigureDevice)

            fun bind(device: BluetoothDeviceEntity, onConfigure: (BluetoothDeviceEntity) -> Unit) {
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
                    txtStatus.setTextColor(itemView.context.getColor(R.color.cyber_teal))
                } else {
                    txtStatus.text = "Vinculado$batteryStr • Notif: $notifBadge"
                    txtStatus.setTextColor(itemView.context.getColor(R.color.on_surface_variant_obsidian))
                }

                btnConfigure.setOnClickListener {
                    onConfigure(device)
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
