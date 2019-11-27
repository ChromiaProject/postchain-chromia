package net.postchain.mc.config.app

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler

class AppConfig(private val config: Configuration) {

    companion object {

        fun fromPropertiesFile(configFile: String): AppConfig {
            val params = Parameters().properties()
                    .setFileName(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration

            return AppConfig(configuration)
        }
    }

    val apiURL: String
        get() = config.getString("api.url", "")

    val brid: String
        get() = config.getString("brid", "")

    val privKey: String
        get() = config.getString("privkey", "")

    val pubKey: String
        get() = config.getString("pubkey", "")
}