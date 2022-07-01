package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandAddNode : CliktCommand(
    name = "add",
    help = "Add or update node information"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    private val host by hostOption().required()

    private val port by portOption().required()

    private val clusterName by option(
        "-c",
        "--cluster",
        help = "comma delimited list of clusters this node belongs to"
    ).required()

    override fun run() {
        CliExecution(config).addNode(key, host, port.toLong(), clusterName)
        println("Node has been added successfully")
    }
}
