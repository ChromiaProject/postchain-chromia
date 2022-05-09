package net.postchain.mc.cli.chromia1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.blockchain.CommandAddBlockchain
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.blockchain.*
import net.postchain.mc.cli.node.*
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.*

class CliC0: CliBase() {
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
            CommandListBlockchainsForNode(),
            CommandListBlockchains(),
            CommandListBlockchainSigners(),
            CommandListBlockchainReplicas(),
            CommandGetBlockchainConfiguration(),
            CommandGetNodeListVersion(),
            CommandListProviderNodes(),
            CommandListProviders(),
            CommandListNodes()
    ).associateBy { it.key() }

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}