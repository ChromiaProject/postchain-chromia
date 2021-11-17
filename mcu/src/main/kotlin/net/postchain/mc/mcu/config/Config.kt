package net.postchain.mc.mcu.config

import net.postchain.mc.mcu.Json
import net.postchain.mc.mcu.model.Dapp
import net.postchain.mc.mcu.model.Node
import java.io.File

data class Config(
    val chain0Name: String,
    val chain0BlockchainRid: String,
    val chain0Config0Path: String,
    val chain0Configs: Map<String, String>,
    val adminPrivKey: String,
    val adminPubKey: String,
    val providerPrivKey: String,
    val providerPubKey: String,
    val nodes: Array<Node> = emptyArray(),
    val dapps: Array<Dapp> = emptyArray()
) {

    companion object {

        const val DEFAULT_FILENAME = "config.cfg"

        fun fromFile(filename: String): Config {
            return fromJson(File(filename).readText())
        }

        fun fromJson(json: String): Config {
            return Json.gson.fromJson(json, Config::class.java)
        }

        fun toFile(config: Config, filename: String) {
            File(filename).writeText(toJson(config))
        }

        fun toJson(config: Config): String {
            return Json.gson.toJson(config)
        }

    }

}