package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListVoterSets : CliktCommand(
    name = "list",
    help = "List all voter sets"
) {
    private val nodeConfig by nodeConfigOption()

    private val includeInactive by includeInactiveOption().deprecated("Not implemented yet")

    override fun run() {
        val sets = CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).listVoterSets()
        sets.forEach {
            println(it.asString())
        }
    }
}