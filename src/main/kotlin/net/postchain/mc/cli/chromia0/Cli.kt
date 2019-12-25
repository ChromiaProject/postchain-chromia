package net.postchain.mc.cli.chromia0

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.blockchain.*
import net.postchain.mc.cli.node.CommandAddNode
import net.postchain.mc.cli.node.CommandRemoveNode
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.CommandAddReplica
import net.postchain.mc.cli.replica.CommandRemoveReplica

class Cli: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandRegisterProvider(),
            CommandUpdateProvider(),
            CommandEnableProvider(),
            CommandDisableProvider(),
            CommandGetProviderInfo(),
            CommandAddNode(),
            CommandGetNodeInfo(),
            CommandRemoveNode(),
            CommandAddReplica(),
            CommandRemoveReplica(),
            CommandAddBlockchain(),
            CommandStopBlockchain(),
            CommandAddConfiguration(),
            CommandAddBlockchainSigners(),
            CommandRemoveBlockchainSigners(),
            CommandListBlockchainsForNode()
    ).map { it.key() to it }.toMap()

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}