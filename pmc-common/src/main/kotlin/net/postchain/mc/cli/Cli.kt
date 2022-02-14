package net.postchain.mc.cli

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.account.CommandKeygen
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command

class Cli: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandKeygen()
    ).map { it.key() to it }.toMap()

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}