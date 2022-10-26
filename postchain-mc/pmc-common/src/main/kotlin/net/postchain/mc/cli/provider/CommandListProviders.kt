package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getAllProviders
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption
import java.lang.StringBuilder

class CommandListProviders : CliktCommand(
        name = "list",
        help = "List all providers"
) {
    private val config by configOption()

    override fun run() {
        println("Providers:")
        table {
            header("Name", "Pubkey", "Is System", "Tier", "Active")
            ClientUtil.fromConfig(config).getAllProviders().forEach {
                row(it.name, it.pubkey.hex(), it.system.toString(), it.tier.toString(), it.active.toString())
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }
                .render(StringBuilder())
                .also { println(it) }
    }
}
