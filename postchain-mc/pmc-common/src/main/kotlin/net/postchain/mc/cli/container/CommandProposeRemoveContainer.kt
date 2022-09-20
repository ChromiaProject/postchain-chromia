package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeRemoveContainer : CliktCommand(
        name = "remove",
        help = "Propose removal of container. Command is irreversible"
) {
    private val config by configOption()

    private val name by nameOption("Container name").required()

    override fun run() {
        CliExecution(config).proposeRemoveContainer(name)
        println("Container has been proposed for removal")
    }
}