package net.postchain.mc.cli

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.client.ConfirmationLevel
import net.postchain.client.DefaultSigner
import net.postchain.client.PostchainClient
import net.postchain.client.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.mc.config.app.AppConfig
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File
import java.time.Instant

class CliExecution {

    private val cryptoSystem = SECP256K1CryptoSystem()
    private val postchainClientFactory = PostchainClientFactory()

    private fun getPostchainClient(configFile: String): PostchainClient {
        val config = AppConfig.fromPropertiesFile(configFile)

        val resolver = postchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.adminPubKey.hexStringToByteArray(), config.adminPrivKey.hexStringToByteArray())
        return postchainClientFactory.getClient(resolver, config.brid.hexStringToByteArray(), DefaultSigner(sigMaker, config.adminPubKey.hexStringToByteArray()))
    }

    private fun getEncodedGtxValueFromFile(blockchainConfigFile: String) :ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    /**
     *
     */
    fun addBlockchainConfiguration(configFile: String, brid: String, height: Long, blockchainConfigFile: String, signer: Pair<ByteArray, ByteArray>) {
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

    /**
     *
     */
    fun addPeer(configFile: String, host: String, port: Long, key: String, signer: Pair<ByteArray, ByteArray>) {
        val client = getPostchainClient(configFile)
        val tx = client.makeTransaction()
        tx.addOperation("add_peer",
                arrayOf(GtvFactory.gtv(host), GtvFactory.gtv(port), GtvFactory.gtv(key.hexStringToByteArray()), GtvFactory.gtv(Instant.now().toEpochMilli())))
        tx.sign(cryptoSystem.buildSigMaker(signer.first, signer.second))
        val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
        if (txResult.status == TransactionStatus.CONFIRMED) {
            println("peer had been added successfully")
        } else {
            throw CliError.Companion.CliException("Cannot add peer")
        }
    }

    /**
     *
     */
    fun removePeer(configFile: String, key: String, signer: Pair<ByteArray, ByteArray>) {
        val client = getPostchainClient(configFile)
        val tx = client.makeTransaction()
        tx.addOperation("remove_peer",
                arrayOf(GtvFactory.gtv(key.hexStringToByteArray())))
        tx.sign(cryptoSystem.buildSigMaker(signer.first, signer.second))
        val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
        if (txResult.status == TransactionStatus.CONFIRMED) {
            println("Peer has been removed")
        } else {
            throw CliError.Companion.CliException("Cannot remove peer")
        }
    }

    /**
     *
     */
    fun addSystemPeer(configFile: String, key: String, signer: Pair<ByteArray, ByteArray>) {
        val client = getPostchainClient(configFile)
        val tx = client.makeTransaction()
        tx.addOperation("add_system_peer",
                arrayOf(GtvFactory.gtv(key.hexStringToByteArray())))
        tx.sign(cryptoSystem.buildSigMaker(signer.first, signer.second))
        tx.post(ConfirmationLevel.VERIFIED).fail {
            throw CliError.Companion.CliException("Cannot add system peer")
        }.success {
            println("System peer has been added")
        }
    }
}