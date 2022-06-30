package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.nodeConfigOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.config.app.BaseClientConfig

class CommandGetVoterSetGovernor : CliktCommand(
    name = "info",
    help = "Get governor of a voter set"
) {
    private val nodeConfig by nodeConfigOption()

    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val governor = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).getVoterSetGovernor(name)
        println("Governor of voter set: $governor")
    }
}
