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
