package net.postchain.cli

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.client.ConfirmationLevel
import net.postchain.client.DefaultSigner
import net.postchain.client.PostchainClient
import net.postchain.client.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.devtools.KeyPairHelper
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File

class CliExecution {

    private val pubKey0 = KeyPairHelper.pubKey(0)
    private val privKey0 = KeyPairHelper.privKey(0)
    private val cryptoSystem = SECP256K1CryptoSystem()
    private val sigMaker0 = cryptoSystem.buildSigMaker(pubKey0, privKey0)
    private val defaultSigner = DefaultSigner(sigMaker0, pubKey0)
    private val postchainClientFactory = PostchainClientFactory()

    private fun getPostchainClient(configFile: String): PostchainClient {
        val config = AppConfig.fromPropertiesFile(configFile)

        val resolver = postchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        return postchainClientFactory.getClient(resolver, config.brid.hexStringToByteArray(), defaultSigner)
    }

    private fun getEncodedGtxValueFromFile(blockchainConfigFile: String) :ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    /**
     *
     */
    fun addBlockchainConfigurtion(configFile: String, brid: String, height: Long, blockchainConfigFile: String, signer: Pair<ByteArray, ByteArray>) {
        val data = getEncodedGtxValueFromFile(blockchainConfigFile)
        val client = getPostchainClient(configFile)
        val tx = client.makeTransaction()
        tx.addOperation("add_blockchain_configuration",
                arrayOf(GtvFactory.gtv(brid.hexStringToByteArray()), GtvFactory.gtv(height), GtvFactory.gtv(data)))
        tx.sign(cryptoSystem.buildSigMaker(signer.first, signer.second))
        tx.post(ConfirmationLevel.VERIFIED).fail {
            throw CliError.Companion.CliException("Cannot add blockchain configuration at $height ")
        }.success {
            println("blockchain configuration at $height was added successfully!")
        }
    }
}