package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.getAllProviders
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.ProvidersPrinter
import net.postchain.mc.cli.util.configOption
import org.spongycastle.asn1.x500.style.RFC4519Style.name

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
