package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.voting.getVoterSets
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import java.lang.StringBuilder

class CommandListVoterSets : CliktCommand(
    name = "list",
    help = "List all voter sets"
) {
    private val config by lazy { read() }

    private val includeInactive by includeInactiveOption().deprecated("Not implemented yet")

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val voterSets = client.getVoterSets()

        println("Voter sets:")
        table {
            header("Name", "Governor", "Majority level")
            voterSets.forEach {
                row(it.name, it.gorvernor, formatThreshold(it.threshold))
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }
            .render(StringBuilder())
            .also { println(it) }
    }
}