package dev.hogballs.goveecontrol

object GoveeCommandBuilder {

    fun build(template: String, params: Map<String, Int> = emptyMap()): ByteArray {
        var hex = template
        for ((key, value) in params) {
            hex = hex.replace("{$key}", "%02x".format(value))
        }
        val raw = hexToBytes(hex)
        val padded = ByteArray(19)
        raw.copyInto(padded)
        var xor: Byte = 0
        for (b in padded) {
            xor = (xor.toInt() xor b.toInt()).toByte()
        }
        return padded + xor
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "")
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
