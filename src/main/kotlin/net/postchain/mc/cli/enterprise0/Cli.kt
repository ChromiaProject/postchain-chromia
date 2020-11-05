package net.postchain.mc.cli.enterprise0

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.blockchain.*
import net.postchain.mc.cli.votingupdates.*
import net.postchain.mc.cli.node.*
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.*

class Cli: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandProposeProvider(),
            CommandUpdateProviderName(),
            CommandProposeEnableProvider(),
            CommandProposeDisableProvider(),
            CommandGetProviderInfo(),
            CommandAddNode(),
            CommandGetNodeInfo(),
            CommandRemoveNode(),
            CommandAddReplica(),
            CommandRemoveReplica(),
            CommandProposeBlockchain(),
            CommandStopBlockchain(),
            CommandProposeConfiguration(),
            CommandAddBlockchainSigners(),
            CommandRemoveBlockchainSigners(),
            CommandListBlockchainsForNode(),
            CommandListAllBlockchains(),
            CommandListActiveBlockchains(),
            CommandListBlockchainSigners(),
            CommandListBlockchainReplicas(),
            CommandGetBlockchainConfiguration(),
            CommandGetNodeListVersion(),
            CommandListProviderNodes(),
            CommandListProviders(),
            CommandListNodes()
    ).map { it.key() to it }.toMap()

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}