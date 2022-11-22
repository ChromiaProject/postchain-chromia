package net.postchain.mc.cli.config

import net.postchain.client.config.PostchainClientConfig
import net.postchain.common.BlockchainRid
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.mc.cli.util.POSTCHAIN_CLIENT_CONFIG
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.bitcoinj.crypto.MnemonicCode
import java.awt.Desktop
import java.io.File
import java.util.*

const val configFileName = ".pmc/config"

object PmcConfigProvider {

    fun fromSystemConfig(): PostchainClientConfig {
        val config = configurationFromSystem()
        return PostchainClientConfig.fromConfiguration(config)
    }

    fun configurationFromSystem(): Configuration {
        val config = globalConfigurationFile().let { if (it.exists()) PropertiesFileLoader.load(it.absolutePath) else PropertiesConfiguration() }
        setValuesFromFile(localConfigurationFile(), config)
        setValuesFromFile(envConfigurationFile(), config)
        return config
    }

    private fun setValuesFromFile(file: File, config: Configuration) {
        if (file.exists()) {
            val c = PropertiesFileLoader.load(file.absolutePath)
            c.keys.forEach { key -> config.setProperty(key, c.getString(key)) }
        }
    }

    fun createConfigFile(file: File, blockchainRid: BlockchainRid?): PostchainClientConfig {
        with(file) {
            parentFile.mkdirs()

            val cs = Secp256K1CryptoSystem()
            val privKey = cs.getRandomBytes(32)
            val mnemonicInstance = MnemonicCode.INSTANCE
            val mnemonic = mnemonicInstance.toMnemonic(privKey).joinToString(" ")
            val pubKey = secp256k1_derivePubKey(privKey)
            val properties = Properties()
            properties["privkey"] = privKey.toHex()
            properties["pubkey"] = pubKey.toHex()
            properties["api.url"] = ""
            properties["brid"] = blockchainRid?.toHex() ?: ""

            outputStream().use {
                properties.store(it, "Keypair generated using secp256k1 for postchain management console")
                it.flush()
            }

            Desktop.getDesktop().edit(this)
            println("Configuration file created in $absolutePath")
            println("Mnemonic: $mnemonic")
            return PostchainClientConfig.fromProperties(absolutePath)
        }
    }

    fun globalConfigurationFile() = File("${System.getProperty("user.home")}/$configFileName")
    fun localConfigurationFile() = File(configFileName)
    fun envConfigurationFile() = System.getenv()[POSTCHAIN_CLIENT_CONFIG]?.let { File(it) } ?: File("")
}
