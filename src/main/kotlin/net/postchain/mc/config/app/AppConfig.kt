package net.postchain.mc.config.app

import net.postchain.common.hexStringToByteArray
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

    val adminPrivKey: String
        get() = config.getString("admin.privkey", "")

    val adminPubKey: String
        get() = config.getString("admin.pubkey", "")
}