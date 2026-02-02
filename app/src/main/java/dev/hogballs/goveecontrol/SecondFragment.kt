package dev.hogballs.goveecontrol

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import dev.hogballs.goveecontrol.databinding.FragmentSecondBinding

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceViewModel by activityViewModels()

    private lateinit var deviceName: String
    private var mac: String = ""
    private var groupName: String = ""

    data class ColorPreset(val name: String, val chipColor: Int, val r: Int, val g: Int, val b: Int)
    data class RawPreset(val name: String, val chipColor: Int, val hex: String)

    private val rawPresets = listOf(
        RawPreset("Warm White", 0xFFFDF4DC.toInt(), "33051501ffffff0898ff98297f"),
        RawPreset("Cool White", 0xFFFFFFFF.toInt(), "33051501ffffff1964fff9fb7f"),
    )

    private val colorPresets = listOf(
        ColorPreset("Red", 0xFFFF0000.toInt(), 255, 0, 0),
        ColorPreset("Orange", 0xFFFF6600.toInt(), 255, 102, 0),
        ColorPreset("Yellow", 0xFFFFFF00.toInt(), 255, 255, 0),
        ColorPreset("Green", 0xFF00FF00.toInt(), 0, 255, 0),
        ColorPreset("Cyan", 0xFF00FFFF.toInt(), 0, 255, 255),
        ColorPreset("Blue", 0xFF0000FF.toInt(), 0, 0, 255),
        ColorPreset("Purple", 0xFF8000FF.toInt(), 128, 0, 255),
        ColorPreset("Pink", 0xFFFF00FF.toInt(), 255, 0, 255),
    )

    companion object {
        private const val TAG = "SecondFragment"
    }

    private val isGroupMode get() = groupName.isNotEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mac = requireArguments().getString("mac", "")
        deviceName = requireArguments().getString("deviceName", "")
        groupName = requireArguments().getString("groupName", "")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.deviceName.text = deviceName

        if (isGroupMode) {
            binding.deviceStatus.text = getString(R.string.status_connected)
        } else {
            val manager = viewModel.getManager(mac)
            updateStatus(manager?.connected == true)
            manager?.onConnectionChange = { connected ->
                view.post { updateStatus(connected) }
            }
        }

        binding.btnPowerOn.setOnClickListener { sendCommand("power_on") }
        binding.btnPowerOff.setOnClickListener { sendCommand("power_off") }

        binding.brightnessSlider.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (!fromUser) return@OnChangeListener
            sendCommand("brightness", mapOf("value" to value.toInt()))
        })

        for (preset in rawPresets) {
            val chip = Chip(requireContext())
            chip.text = preset.name
            chip.isCheckable = true
            chip.chipBackgroundColor = ColorStateList.valueOf(preset.chipColor)
            chip.setTextColor(Color.BLACK)
            chip.setOnClickListener {
                val data = GoveeCommandBuilder.build(preset.hex)
                if (isGroupMode) {
                    val group = viewModel.config.groups.find { it.name == groupName } ?: return@setOnClickListener
                    for (m in group.devices) { viewModel.getManager(m)?.sendCommand(data) }
                } else {
                    viewModel.getManager(mac)?.sendCommand(data)
                }
            }
            binding.presetChips.addView(chip)
        }

        for (preset in colorPresets) {
            val chip = Chip(requireContext())
            chip.text = preset.name
            chip.isCheckable = true
            chip.chipBackgroundColor = ColorStateList.valueOf(preset.chipColor)
            val lum = (preset.r * 0.299 + preset.g * 0.587 + preset.b * 0.114)
            chip.setTextColor(if (lum > 150) Color.BLACK else Color.WHITE)
            chip.setOnClickListener {
                binding.sliderRed.value = preset.r.toFloat()
                binding.sliderGreen.value = preset.g.toFloat()
                binding.sliderBlue.value = preset.b.toFloat()
                updateColorPreview()
                sendCommand("color", mapOf("r" to preset.r, "g" to preset.g, "b" to preset.b))
            }
            binding.presetChips.addView(chip)
        }

        updateColorPreview()
        val colorChangeListener = Slider.OnChangeListener { _, _, _ -> updateColorPreview() }
        binding.sliderRed.addOnChangeListener(colorChangeListener)
        binding.sliderGreen.addOnChangeListener(colorChangeListener)
        binding.sliderBlue.addOnChangeListener(colorChangeListener)

        binding.btnSendColor.setOnClickListener {
            val r = binding.sliderRed.value.toInt()
            val g = binding.sliderGreen.value.toInt()
            val b = binding.sliderBlue.value.toInt()
            sendCommand("color", mapOf("r" to r, "g" to g, "b" to b))
        }
    }

    private fun sendCommand(commandName: String, params: Map<String, Int> = emptyMap()) {
        if (isGroupMode) {
            val group = viewModel.config.groups.find { it.name == groupName } ?: return
            viewModel.sendGroupCommand(group, commandName, params)
        } else {
            val device = viewModel.config.deviceByMac(mac) ?: return
            val model = viewModel.config.modelFor(device) ?: return
            val template = model.commands[commandName] ?: return
            val data = GoveeCommandBuilder.build(template, params)
            viewModel.getManager(mac)?.sendCommand(data)
        }
    }

    private fun updateStatus(connected: Boolean) {
        binding.deviceStatus.text = getString(
            if (connected) R.string.status_connected else R.string.status_disconnected
        )
    }

    private fun updateColorPreview() {
        val r = binding.sliderRed.value.toInt()
        val g = binding.sliderGreen.value.toInt()
        val b = binding.sliderBlue.value.toInt()
        binding.colorPreview.setBackgroundColor(Color.rgb(r, g, b))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
