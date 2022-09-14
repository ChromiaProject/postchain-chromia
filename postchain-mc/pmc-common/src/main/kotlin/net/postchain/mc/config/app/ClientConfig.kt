package net.postchain.mc.config.app

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler


interface ClientConfig {
    val apiURL: String
    val brid: String
    val privKey: String
    val pubKey: String
}

open class DelegatingClientConfig(private val delegate: ClientConfig) : ClientConfig by delegate

open class BaseClientConfig(private val config: Configuration) : ClientConfig {

    companion object {

        fun fromPropertiesFile(configFile: String): ClientConfig {
            val params = Parameters().properties()
                    .setFileName(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration

            return BaseClientConfig(configuration)
        }
    }

    override val apiURL: String
        get() = config.getString("api-url", "")

    override val brid: String
        get() = config.getString("blockchain-rid", "")

    override val privKey: String
        get() = config.getString("privkey", "")

    override val pubKey: String
        get() = config.getString("pubkey", "")
}