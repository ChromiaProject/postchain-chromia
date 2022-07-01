package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption

class CommandListVoterSets : CliktCommand(
    name = "list",
    help = "List all voter sets"
) {
    private val config by configOption()

    private val includeInactive by includeInactiveOption().deprecated("Not implemented yet")

    override fun run() {
        val sets = CliExecutionD1(config).listVoterSets()
        sets.forEach {
            println(it.asString())
        }
    }
}