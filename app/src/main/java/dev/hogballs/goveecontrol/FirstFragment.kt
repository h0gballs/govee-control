package dev.hogballs.goveecontrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dev.hogballs.goveecontrol.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by activityViewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) buildDeviceList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }
        buildDeviceList()
    }

    @SuppressLint("MissingPermission")
    private fun buildDeviceList() {
        val config = viewModel.config
        val inflater = LayoutInflater.from(requireContext())

        binding.groupsContainer.removeAllViews()
        for (group in config.groups) {
            val card = inflater.inflate(R.layout.item_group, binding.groupsContainer, false)
            card.findViewById<TextView>(R.id.group_name).text = group.name
            card.findViewById<Button>(R.id.btn_all_on).setOnClickListener {
                viewModel.sendGroupCommand(group, "power_on")
            }
            card.findViewById<Button>(R.id.btn_all_off).setOnClickListener {
                viewModel.sendGroupCommand(group, "power_off")
            }
            card.findViewById<Button>(R.id.btn_group_control).setOnClickListener {
                val bundle = bundleOf(
                    "groupName" to group.name,
                    "deviceName" to group.name
                )
                findNavController().navigate(
                    R.id.action_FirstFragment_to_SecondFragment, bundle
                )
            }
            binding.groupsContainer.addView(card)
        }

        binding.devicesContainer.removeAllViews()
        for (device in config.devices) {
            val card = inflater.inflate(R.layout.item_device, binding.devicesContainer, false)
            val nameView = card.findViewById<TextView>(R.id.device_name)
            val statusView = card.findViewById<TextView>(R.id.device_status)
            val connectBtn = card.findViewById<Button>(R.id.btn_connect)
            val controlBtn = card.findViewById<Button>(R.id.btn_control)

            nameView.text = device.name

            val manager = viewModel.getOrCreateManager(device.mac)

            fun updateUi(connected: Boolean) {
                view?.post {
                    statusView.text = getString(
                        if (connected) R.string.status_connected else R.string.status_disconnected
                    )
                    connectBtn.text = getString(
                        if (connected) R.string.disconnect else R.string.connect
                    )
                    connectBtn.isEnabled = true
                    controlBtn.isEnabled = connected
                }
            }

            manager?.onConnectionChange = { connected -> updateUi(connected) }
            updateUi(manager?.connected == true)

            connectBtn.setOnClickListener {
                val m = viewModel.getOrCreateManager(device.mac) ?: return@setOnClickListener
                if (m.connected) {
                    m.disconnect()
                    updateUi(false)
                } else {
                    statusView.text = getString(R.string.status_connecting)
                    connectBtn.isEnabled = false
                    m.connect()
                }
            }

            controlBtn.setOnClickListener {
                val bundle = bundleOf("mac" to device.mac, "deviceName" to device.name)
                findNavController().navigate(
                    R.id.action_FirstFragment_to_SecondFragment, bundle
                )
            }

            binding.devicesContainer.addView(card)
        }

        // Auto-connect all devices on startup
        for (device in config.devices) {
            val m = viewModel.getOrCreateManager(device.mac) ?: continue
            if (!m.connected) m.connect()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
