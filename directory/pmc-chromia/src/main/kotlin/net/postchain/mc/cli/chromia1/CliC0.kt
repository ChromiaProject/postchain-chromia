package net.postchain.mc.cli.chromia1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.account.CommandKeygen
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.provider.*

class CliC0: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandKeygen(),
            CommandRegisterProvider(),
            CommandUpdateProvider(),
            CommandGetProviderInfo(),


            CommandListProviderNodes(),
            CommandListProviders(),
    ).associateBy { it.key() }

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}