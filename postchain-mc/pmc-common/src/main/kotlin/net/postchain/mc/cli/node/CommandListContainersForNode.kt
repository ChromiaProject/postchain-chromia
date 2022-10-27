package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.table
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.ContainersPrinter
import net.postchain.mc.cli.util.configOption

class CommandListContainersForNode : CliktCommand(
    name = "containers",
    help = "List containers for node"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        table {
            CliExecution(config).listContainersForNode(key).forEach {

                row(it.name, it.cluster, it.deployer)
            }
        }.render().also { println(it) }
    }
}