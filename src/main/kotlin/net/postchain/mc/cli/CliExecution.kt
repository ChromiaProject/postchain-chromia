package net.postchain.mc.cli

import mu.KLogging
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.client.ConfirmationLevel
import net.postchain.client.DefaultSigner
import net.postchain.client.PostchainClient
import net.postchain.client.PostchainClientFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.mc.config.app.AppConfig
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import org.apache.commons.configuration2.ex.ConfigurationException
import java.io.File

class CliExecution {

    companion object : KLogging()

    private val cryptoSystem = SECP256K1CryptoSystem()
    private val postchainClientFactory = PostchainClientFactory()

    private fun getPostchainClient(config: AppConfig): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty() || config.privKey.isEmpty()) {
            throw UserMistake("missing required parameters")
        }
        val resolver = postchainClientFactory.makeSimpleNodeResolver(config.apiURL)
        val sigMaker = cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
        return postchainClientFactory.getClient(resolver, config.brid.hexStringToByteArray(), DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray()))
    }

    private fun getEncodedGtxValueFromFile(blockchainConfigFile: String) :ByteArray {
        val gtv =  GtvMLParser.parseGtvML(File(blockchainConfigFile).readText())
        return GtvEncoder.encodeGtv(gtv)
    }

    /**
     *
     */
    fun addBlockchain(configFile: String, blockchainConfigFile: String, nodes: String) {
        try {
            val config = AppConfig.fromPropertiesFile(configFile)
            val client = getPostchainClient(config)
            val data = getEncodedGtxValueFromFile(blockchainConfigFile)
            val nodeList = nodes.split(",").map { client.query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get() }
            val tx = client.makeTransaction()
            tx.addOperation("add_blockchain",
                    arrayOf(GtvFactory.gtv(data), GtvFactory.gtv(nodeList)))
            tx.sign(cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray()))
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("blockchain was added successfully!")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain")
            }
        } catch (e: ConfigurationException) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Config file not found $configFile")
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun addNode(configFile: String, key: String, host: String, port: Long) {
        try {
            val config = AppConfig.fromPropertiesFile(configFile)
            val client = getPostchainClient(config)
            val provider = client.query("get_provider", GtvFactory.gtv(
                        "pubkey" to GtvFactory.gtv(config.pubKey.hexStringToByteArray()))).get()
            val tx = client.makeTransaction()
            tx.addOperation("add_node",
                    arrayOf(GtvFactory.gtv(provider.asByteArray()), GtvFactory.gtv(key.hexStringToByteArray()), GtvFactory.gtv(host), GtvFactory.gtv(port)))
            tx.sign(cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray()))
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("node had been added successfully")
            } else {
                throw CliError.Companion.CliException("Cannot add node")
            }
        } catch (e: ConfigurationException) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Config file not found $configFile")
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun addBlockchainSigners(configFile: String, brid: String, signers: String) {
        try {
            val config = AppConfig.fromPropertiesFile(configFile)
            val client = getPostchainClient(config)
            val nodeList = signers.split(",").map {
                client.query("get_node", GtvFactory.gtv("pubkey" to GtvFactory.gtv(it.hexStringToByteArray()))).get()
            }
            val blockchain = client.query("get_blockchain", GtvFactory.gtv("rid" to GtvFactory.gtv(brid.hexStringToByteArray()))).get()
            val tx = client.makeTransaction()
            tx.addOperation("add_blockchain_signers", arrayOf(blockchain, GtvFactory.gtv(nodeList)))
            tx.sign(cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray()))
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Blockchain's signers have been added")
            } else {
                throw CliError.Companion.CliException("Cannot add blockchain's signers")
            }
        } catch (e: ConfigurationException) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Config file not found $configFile")
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun registerProvider(configFile: String, key: String) {
        try {
            val config = AppConfig.fromPropertiesFile(configFile)
            val client = getPostchainClient(config)
            val tx = client.makeTransaction()
            tx.addOperation("register_provider",
                    arrayOf(GtvFactory.gtv(key.hexStringToByteArray())))
            tx.sign(cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray()))
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been added")
            } else {
                throw CliError.Companion.CliException("Cannot add provider")
            }
        } catch (e: ConfigurationException) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Config file not found $configFile")
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }

    /**
     *
     */
    fun enableProvider(configFile: String, key: String) {
        try {
            val config = AppConfig.fromPropertiesFile(configFile)
            val client = getPostchainClient(config)
            val provider = client.query("get_provider", GtvFactory.gtv(
                    "pubkey" to GtvFactory.gtv(key.hexStringToByteArray()))).get()
            val tx = client.makeTransaction()
            tx.addOperation("enable_provider", arrayOf(provider))
            tx.sign(cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray()))
            val txResult = tx.postSync(ConfirmationLevel.UNVERIFIED)
            if (txResult.status == TransactionStatus.CONFIRMED) {
                println("Provider has been enable")
            } else {
                throw CliError.Companion.CliException("Cannot enable provider")
            }
        } catch (e: ConfigurationException) {
            logger.error(e.message)
            throw CliError.Companion.CliException("Config file not found $configFile")
        } catch (e: UserMistake) {
            logger.error(e.message)
            throw CliError.Companion.CliException("User Mistake: Input parameters might be wrong or missing")
        } catch (e: Exception) {
            logger.error(e.message)
            throw CliError.Companion.CliException("System Error: Something wrong happen")
        }
    }
}