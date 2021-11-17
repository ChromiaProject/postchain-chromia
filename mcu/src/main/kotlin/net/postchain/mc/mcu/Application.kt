package net.postchain.mc.mcu

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.postchain.mc.mcu.config.Config
import net.postchain.mc.mcu.model.Dapp
import net.postchain.mc.mcu.plugins.configureHTTP
import net.postchain.mc.mcu.plugins.configureRouting
import net.postchain.mc.mcu.plugins.configureSerialization
import net.postchain.mc.mcu.plugins.configureTemplating
import net.postchain.mc.mcu.ps.Postchain
import java.io.File
import kotlin.io.path.Path

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {

        when {
            args[0] == "help" -> usage()
            args[0] == "make-dapps-config" -> makeDappsConfig(args[1])
        }

    } else {

        embeddedServer(Netty, port = 7628, host = "0.0.0.0") {

            val config = Config.fromFile(Config.DEFAULT_FILENAME)
            val postchain = Postchain(config)
            val ctx = Context(config, postchain)

            configureRouting(ctx)
            configureHTTP(ctx)
            configureTemplating(ctx)
            configureSerialization(ctx)

        }.start(wait = true)

    }
}

private fun usage() {
    println(
        """
        mcu help                            prints this message
        mcu make-dapps-config <path>        writes <path>'s dapps json config to 'dapps-config.json' 
    """.trimIndent()
    )
}

private fun makeDappsConfig(path: String) {
    val dapps = mutableListOf<Dapp>()

    File(path).listFiles().forEach {
        if (it.isDirectory) {
            val deepPath = Path(path, it.name, "target", "blockchains", "0")
            val dapp = Dapp(
                it.name,
                deepPath.resolve("brid.txt").toFile().readText(),
                deepPath.resolve("0.xml").toString(),
                true,
                "cont1",
                0
            )

            dapps.add(dapp)
        }
    }

    val json = Json.gson.toJson(dapps)
    File("dapps-config.json").writeText(json)
}