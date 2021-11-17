package net.postchain.mc.mcu.plugins

import freemarker.cache.ClassTemplateLoader
import io.ktor.application.*
import io.ktor.freemarker.*
import io.ktor.response.*
import io.ktor.routing.*
import net.postchain.mc.mcu.Context

fun Application.configureTemplating(ctx: Context) {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }

    routing {
        get("/") {
            val model = mutableMapOf<String, Any?>()
            model["node0"] = ctx.config.nodes.firstOrNull()
            model["chain0Name"] = ctx.config.chain0Name
            ctx.config.dapps.forEachIndexed { i, dapp ->
                model["dapp$i"] = dapp
            }

            val resp = FreeMarkerContent("index.ftl", model, "")
            call.respond(resp)
        }
    }
}
