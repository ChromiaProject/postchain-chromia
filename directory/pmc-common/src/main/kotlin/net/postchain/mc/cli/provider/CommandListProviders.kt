package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.queries.getAllProviders
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandListProviders : CliktCommand(
    name = "list",
    help = "List all providers"
) {
    private val config by configOption()

    override fun run() {
        ClientUtil.fromConfig(config).getAllProviders().joinToString("\n\n") {
            """
            Provider: ${it.name}
            Pubkey: ${it.pubkey.toHex()}
            System: ${it.system}
            Tier: ${it.tier}
            Active: ${it.active}
            """.trimIndent()
        }.let { println(it) }
    }
}
