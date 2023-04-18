package net.postchain.directory1.test

import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.io.File

class BlockchainConfig(
        val hash: WrappedByteArray,
        val data: ByteArray
) {

    companion object {
        private val cryptoSystem = Secp256K1CryptoSystem()

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
            return BlockchainConfig(hash.wrap(), data)
        }
    }
}