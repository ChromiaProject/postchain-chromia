package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.configOption

class CommandInit : CliktCommand(
    name = "initialize",
    help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val config by configOption()

    private val host by hostOption().required()

    private val port by portOption().required()

    override fun run() {
        CliExecutionD1(config).init(host, port.toLong())
    }
}