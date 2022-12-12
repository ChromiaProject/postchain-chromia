package net.postchain.mc

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.postchain.mc.cli.ManagementConsole
import net.postchain.mc.cli.provider.CommandUpdateProvider

fun main(args: Array<String>) = object : ManagementConsole() {
    override fun beforeCommandAdded(command: CliktCommand) {
        if (command.commandName == "provider") {
            command.subcommands(CommandUpdateProvider())
        }
    }
}.main(args)
