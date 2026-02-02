package dev.hogballs.goveecontrol

import android.content.Context
import org.json.JSONObject

data class ModelConfig(
    val serviceUuid: String,
    val writeCharUuid: String,
    val commands: Map<String, String>
)

data class DeviceConfig(
    val name: String,
    val mac: String,
    val model: String
)

data class GroupConfig(
    val name: String,
    val devices: List<String>
)

data class GoveeConfig(
    val models: Map<String, ModelConfig>,
    val devices: List<DeviceConfig>,
    val groups: List<GroupConfig>
) {
    fun modelFor(device: DeviceConfig): ModelConfig? = models[device.model]

    fun deviceByMac(mac: String): DeviceConfig? = devices.find { it.mac == mac }

    companion object {
        fun load(context: Context): GoveeConfig {
            val json = context.assets.open("govee_config.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            val modelsObj = root.getJSONObject("models")
            val models = mutableMapOf<String, ModelConfig>()
            for (key in modelsObj.keys()) {
                val m = modelsObj.getJSONObject(key)
                val cmdsObj = m.getJSONObject("commands")
                val cmds = mutableMapOf<String, String>()
                for (ck in cmdsObj.keys()) {
                    cmds[ck] = cmdsObj.getString(ck)
                }
                models[key] = ModelConfig(
                    serviceUuid = m.getString("service_uuid"),
                    writeCharUuid = m.getString("write_char_uuid"),
                    commands = cmds
                )
            }

            val devArr = root.getJSONArray("devices")
            val devices = mutableListOf<DeviceConfig>()
            for (i in 0 until devArr.length()) {
                val d = devArr.getJSONObject(i)
                devices.add(DeviceConfig(
                    name = d.getString("name"),
                    mac = d.getString("mac"),
                    model = d.getString("model")
                ))
            }

            val grpArr = root.getJSONArray("groups")
            val groups = mutableListOf<GroupConfig>()
            for (i in 0 until grpArr.length()) {
                val g = grpArr.getJSONObject(i)
                val devList = mutableListOf<String>()
                val arr = g.getJSONArray("devices")
                for (j in 0 until arr.length()) {
                    devList.add(arr.getString(j))
                }
                groups.add(GroupConfig(name = g.getString("name"), devices = devList))
            }

            return GoveeConfig(models, devices, groups)
        }
    }
}
