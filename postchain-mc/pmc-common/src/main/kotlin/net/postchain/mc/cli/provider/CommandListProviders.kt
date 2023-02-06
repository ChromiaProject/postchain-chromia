package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getAllProviders
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandListProviders : CliktCommand(
        name = "list",
        help = "List all providers"
) {
    private val config by configOption()

    override fun run() {
        val providers = ClientUtil.fromConfig(config).getAllProviders()
        if (providers.isEmpty()) {
            echo("No providers")
        } else {
            echo("Providers:")
            table {
                header("Name", "Url", "Pubkey", "Is System", "Tier", "Active")
                providers.forEach {
                    row(it.name, it.url, it.pubkey.hex(), it.system.toString(), it.tier.toString(), it.active.toString())
                }
                hints {
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                }
            }.render().also { echo(it) }
        }
    }
}
