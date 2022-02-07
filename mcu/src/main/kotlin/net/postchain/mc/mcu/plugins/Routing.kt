package net.postchain.mc.mcu.plugins

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import net.postchain.common.toHex
import net.postchain.mc.mcu.Context
import kotlin.random.Random

fun Application.configureRouting(ctx: Context) {

    routing {
        get("/ping") {
            call.respondText("pong")
        }

        post("/actions/init") {
            log.info("init chain0")
            ctx.postchain.initDevnet()
        }

        post("/actions/launch/{dappName}") {
            val dappName = call.parameters["dappName"]
            log.info("launch $dappName")
            if (dappName != null) {
                ctx.postchain.launchBlockchain(dappName)
            }
        }

        post("/actions/pause/{dappName}") {
            val dappName = call.parameters["dappName"]
            log.info("pause $dappName")
            if (dappName != null) {
                ctx.postchain.pauseBlockchain(dappName)
            }
        }

        post("/actions/resume/{dappName}") {
            val dappName = call.parameters["dappName"]
            log.info("resume $dappName")
            if (dappName != null) {
                ctx.postchain.resumeBlockchain(dappName)
            }
        }

        post("/actions/configure/{dappName}/{configName}") {
            val dappName = call.parameters["dappName"]
            val configName = call.parameters["configName"]
            log.info("configure $dappName with config $configName")
            if (dappName != null && configName != null) {
                ctx.postchain.configureBlockchain(dappName, configName)
            }
        }

        post("/actions/tx/{dappName}/{opName}") {
            val dappName = call.parameters["dappName"]
            val opName = call.parameters["opName"]
            log.info("post tx [$opName(...)] to $dappName")
            if (dappName != null && opName != null) {
                val blobName = Random.Default.nextBytes(64).toHex()
                val blob = Random.Default.nextBytes(1_000_000)
                ctx.postchain.postTx(dappName, opName, blobName, blob)
            }
        }

        // Static plugin. Try to access `/static/index.html`
        static("/static") {
            resources("static")
        }
    }

}
