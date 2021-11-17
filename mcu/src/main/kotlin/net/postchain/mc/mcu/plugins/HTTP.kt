package net.postchain.mc.mcu.plugins

import io.ktor.http.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import net.postchain.mc.mcu.Context

fun Application.configureHTTP(ctx: Context) {
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header("MyCustomHeader")
        allowCredentials = true
    }

}
