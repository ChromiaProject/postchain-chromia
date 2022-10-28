package net.postchain.mc.cli.util

import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File

fun readConfigurationFile(blockchainConfigFile: File, format: String?): ByteArray {
    var fmt = format
    if (fmt == null) {
        fmt = if (blockchainConfigFile.extension == "gtv") "gtv" else "xml"
    }
    val data: ByteArray
    if (fmt == "gtv") {
        data = blockchainConfigFile.readBytes()
        // try to decode to ensure data is valid
        GtvFactory.decodeGtv(data)
    } else {
        data = getEncodedGtxValueFromFile(blockchainConfigFile)
    }
    return data
}

private fun getEncodedGtxValueFromFile(blockchainConfigFile: File): ByteArray {
    val gtv = GtvMLParser.parseGtvML(blockchainConfigFile.readText())
    return GtvEncoder.encodeGtv(gtv)
}
