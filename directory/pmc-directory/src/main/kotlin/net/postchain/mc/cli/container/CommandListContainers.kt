package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.mc.cli.util.ContainersPrinter
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.configOption

class CommandListContainers : CliktCommand(
        name = "list",
        help = "List all existing containers"
) {
    private val config by configOption()

    override fun run() {
        val containers = CliExecutionD1(config).listContainers()
        val res = ContainersPrinter.print(containers, true)
        println(res)
        println("Query returned successfully")
    }
}