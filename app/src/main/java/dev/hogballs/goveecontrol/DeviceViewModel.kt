package dev.hogballs.goveecontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    val config: GoveeConfig by lazy { GoveeConfig.load(app) }

    private val bleManagers = mutableMapOf<String, GoveeBleManager>()

    fun getOrCreateManager(mac: String): GoveeBleManager? {
        bleManagers[mac]?.let { return it }
        val device = config.deviceByMac(mac) ?: return null
        val model = config.modelFor(device) ?: return null
        val manager = GoveeBleManager(getApplication(), device, model)
        bleManagers[mac] = manager
        return manager
    }

    fun getManager(mac: String): GoveeBleManager? = bleManagers[mac]

    fun disconnectAll() {
        for (manager in bleManagers.values) {
            manager.disconnect()
        }
    }

    fun reconnectAll() {
        for (manager in bleManagers.values) {
            if (!manager.connected) manager.connect()
        }
    }

    fun sendGroupCommand(group: GroupConfig, commandName: String) {
        sendGroupCommand(group, commandName, emptyMap())
    }

    fun sendGroupCommand(group: GroupConfig, commandName: String, params: Map<String, Int>) {
        for (mac in group.devices) {
            val manager = bleManagers[mac] ?: continue
            if (!manager.connected) continue
            val device = config.deviceByMac(mac) ?: continue
            val model = config.modelFor(device) ?: continue
            val template = model.commands[commandName] ?: continue
            val data = GoveeCommandBuilder.build(template, params)
            manager.sendCommand(data)
        }
    }

    override fun onCleared() {
        super.onCleared()
        for (manager in bleManagers.values) {
            manager.disconnect()
        }
        bleManagers.clear()
    }
}
