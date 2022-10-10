package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.ContainersPrinter
import net.postchain.mc.cli.util.configOption

class CommandListContainersForNode : CliktCommand(
    name = "containers",
    help = "List containers for node"
) {
    private val config by lazy { read() }

    private val key by requiredPubkeyOption()

    override fun run() {
        val containers = CliExecution(config).listContainersForNode(key)
        val res = ContainersPrinter.print(containers, false)
        println(res)
        println("Query returned successfully")
    }
}