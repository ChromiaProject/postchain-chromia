package net.postchain.mc.cli.util

import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.mc.cli.base.cryptoSystem
import java.io.File

class BlockchainConfig(
        val hash: WrappedByteArray,
        val data: ByteArray,
        val gtv: Gtv
) {

    companion object {
        fun readFromFile(blockchainConfigFile: File): BlockchainConfig {
            val (gtv, data) = if (blockchainConfigFile.extension == "gtv") {
                val data = blockchainConfigFile.readBytes()
                val gtv = GtvFactory.decodeGtv(data)
                gtv to data
            } else {
                val gtv = GtvMLParser.parseGtvML(blockchainConfigFile.readText())
                val data = GtvEncoder.encodeGtv(gtv)
                gtv to data
            }

            val hash = gtv.merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            return BlockchainConfig(hash.wrap(), data, gtv)
        }
    }
}