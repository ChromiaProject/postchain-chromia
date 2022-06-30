package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.common.toHex
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandListVoterSetMembers : CliktCommand(
    name = "info",
    help = "Show members of a voter set"
) {
    private val nodeConfig by nodeConfigOption()
    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val members = CliExecutionD1(BaseClientConfig.fromPropertiesFile(nodeConfig)).listVoterSetMembers(name)
        members.forEach {
            println(it.asByteArray().toHex())
        }
        if (members.isEmpty()) {
            println("Voter set has no members.")
        }
    }
}