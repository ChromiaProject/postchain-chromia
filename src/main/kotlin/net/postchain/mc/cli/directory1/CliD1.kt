package net.postchain.mc.cli.directory1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.blockchain.*
import net.postchain.mc.cli.cluster.*
import net.postchain.mc.cli.node.*
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.*
import net.postchain.mc.cli.votingupdates.*

class CliD1: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandInit(),
            CommandRegisterProvider(),
            CommandListProviders(),
            CommandUpdateProviderName(),
            CommandProposeEnableProvider(),
            CommandProposeDisableProvider(),
            CommandTransferActionPoints(),
            CommandGetProviderInfo(),

            CommandAddCluster(),
            CommandListClusters(),
            CommandProposeClusterProvider(),
            CommandProposeClusterResourceLimits(),
            CommandProposeContainer(),
            CommandProposeContainerResourceLimits(),
            CommandProposeClusterDeployer(),

            CommandAddNode(),
            CommandListProviderNodes(),
            CommandListNodes(),
            CommandGetNodeInfo(),
            CommandRemoveNode(),

            CommandAddBlockchainReplica(),
            CommandListBlockchainReplicas(),
            CommandRemoveBlockchainReplica(),
            CommandAddContainerReplica(),
            CommandRemoveContainerReplica(),

            CommandProposeBlockchain(),
            CommandListBlockchainsForNode(),
            CommandListBlockchains(),
            CommandProposeConfiguration(),
            CommandGetBlockchainConfiguration(),

            CommandProposePauseBlockchain(),
            CommandProposeUnPauseBlockchain(),
            CommandProposeDeleteBlockchain(),

            CommandGetProposal(),
            CommandListProposalsSince(),
            CommandCreateVoterSet(),
            CommandListVoterSetMembers(),
            CommandProposeVoterSetMember(),
            CommandListVoterSets(),
            CommandProposeVoterSetGovernor(),
            CommandGetVoterSetGovernor(),
            CommandVote(),

            CommandListBlockchainSigners(),
            CommandGetNodeListVersion()
            ).map { it.key() to it }.toMap()

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}