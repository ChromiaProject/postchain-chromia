package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandGetVoterSetGovernor : CliktCommand(
    name = "info",
    help = "Get governor of a voter set"
) {
    private val config by configOption()

    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val governor = CliExecution(config).getVoterSetGovernor(name)
        println("Governor of voter set: $governor")
    }
}
