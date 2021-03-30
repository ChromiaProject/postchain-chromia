package net.postchain.mc.cli.directory1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.blockchain.*
import net.postchain.mc.cli.cluster.CommandAddCluster
import net.postchain.mc.cli.cluster.CommandInit
import net.postchain.mc.cli.node.*
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.*
import net.postchain.mc.cli.votingupdates.*

class CliD1: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandInit(),
            CommandRegisterProvider(),
            CommandUpdateProviderName(),
            CommandProposeEnableProvider(),
            CommandProposeDisableProvider(),
            CommandTransferActionPoints(),
            CommandGetProviderInfo(),

            CommandAddCluster(),
            CommandProposeClusterResourceLimits(),
            CommandProposeContainer(),
            CommandProposeContainerResourceLimits(),

            CommandAddNode(),
            CommandGetNodeInfo(),
            CommandRemoveNode(),

            CommandAddBlockchainReplica(),
            CommandRemoveBlockchainReplica(),
            CommandAddContainerReplica(),
            CommandRemoveContainerReplica(),

            CommandProposeBlockchain(),
            CommandProposeConfiguration(),

            CommandProposePauseBlockchain(),
            CommandProposeUnPauseBlockchain(),
            CommandProposeDeleteBlockchain(),

            CommandListBlockchainsForNode(),
            CommandListBlockchains(),
            CommandListBlockchainSigners(),
            CommandListBlockchainReplicas(),
            CommandGetBlockchainConfiguration(),
            CommandGetNodeListVersion(),
            CommandListProviderNodes(),
            CommandListProviders(),
            CommandListNodes(),
            CommandListProposalsSince(),

            CommandGetProposal(),
            CommandCreateVoterSet(),
            CommandVote()
    ).map { it.key() to it }.toMap()

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}