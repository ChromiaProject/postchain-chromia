package net.postchain.mc.cli.util

import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.mc.cli.base.cryptoSystem
import java.io.File

class BlockchainConfig(
        val blockchainRid: BlockchainRid,
        val data: ByteArray
)

fun readConfigurationFile(blockchainConfigFile: File, format: String?): BlockchainConfig {
    var fmt = format
    if (fmt == null) {
        fmt = if (blockchainConfigFile.extension == "gtv") "gtv" else "xml"
    }

    val (gtv, data) = if (fmt == "gtv") {
        val data = blockchainConfigFile.readBytes()
        val gtv = GtvFactory.decodeGtv(data)
        gtv to data
    } else {
        val gtv = GtvMLParser.parseGtvML(blockchainConfigFile.readText())
        val data = GtvEncoder.encodeGtv(gtv)
        gtv to data
    }

    val brid = BlockchainRid(gtv.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))
    return BlockchainConfig(brid, data)
}
