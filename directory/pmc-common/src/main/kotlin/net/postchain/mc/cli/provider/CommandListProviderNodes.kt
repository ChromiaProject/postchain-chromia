package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import java.time.Instant
import java.util.Date

class CommandListProviderNodes : CliktCommand(
    name = "nodes",
    help = "List nodes by provider"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    private val verbose by option("-v", "--verbose", help = "Show verbose output").flag()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        ClientUtil.fromConfig(config).getNodesByProvider(key.hexStringToByteArray()).joinToString("\n") {
            if (!verbose) {
                it.pubkey.toHex()
            } else {
                """
                    Node: ${it.pubkey.toHex()}
                    Host: ${it.host}
                    Port: ${it.port}
                    Active: ${it.active}
                    Last updated: ${Date.from(Instant.ofEpochMilli(it.lastUpdated))}
                    -----------------------------------------------
                """.trimIndent()
            }
        }.let { println(it) }
    }
}