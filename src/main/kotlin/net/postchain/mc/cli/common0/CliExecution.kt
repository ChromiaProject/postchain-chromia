package net.postchain.mc.cli.common0

import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SigMaker
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.mc.config.app.ClientConfig
import java.io.File
import java.time.Instant

abstract class CliExecution(val config: ClientConfig) {
    protected val cryptoSystem = SECP256K1CryptoSystem()
    protected fun getPostchainClient(): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty() || config.privKey.isEmpty()) {
            throw UserMistake("missing required parameters")
        }
        val resolver = PostchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
        val defaultSigner = DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray())
        return PostchainClientFactory.getClient(resolver, BlockchainRid.buildFromHex(config.brid), defaultSigner)
    }

    protected fun getEncodedGtxValueFromFile(blockchainConfigFile: String) : ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    protected fun buildSigMaker(): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }

    protected fun makeTransactionWithNop(): GTXTransactionBuilder {
        return getPostchainClient().makeTransaction().apply {
            addOperation("nop", arrayOf(GtvFactory.gtv(Instant.now().toEpochMilli())))
        }
    }

    abstract fun registerProvider(providerPublicKey: String)

    abstract fun enableProvider(key: String)
    abstract fun addNode(nodeKey: String, host: String, port: Long)
    abstract fun addConfiguration(blockchainRID: String, blockchainConfigFile: String, height: Long, format: String?)
    abstract fun addBlockchain(blockchainConfigFile: String, nodes: String, format: String?)
    abstract fun listNodesWithProvider(): List<Gtv>
}