package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandListVoterSetMembers : CliktCommand(
    name = "info",
    help = "Show members of a voter set"
) {
    private val config by configOption()
    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val members = CliExecution(config).listVoterSetMembers(name)
        members.forEach {
            println(it.asByteArray().toHex())
        }
        if (members.isEmpty()) {
            println("Voter set has no members.")
        }
    }
}