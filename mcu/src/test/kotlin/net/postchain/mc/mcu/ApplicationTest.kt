package net.postchain.mc.mcu

import io.ktor.http.*
import io.ktor.server.testing.*
import net.postchain.mc.mcu.config.Config
import net.postchain.mc.mcu.plugins.configureRouting
import net.postchain.mc.mcu.ps.Postchain
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() {
        val config = Config.fromFile("no-file")
        val postchain = Postchain(config)
        val ctx = Context(config, postchain)

        withTestApplication({ configureRouting(ctx) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("Hello World!", response.content)
            }
        }
    }
}