package net.postchain.mc.cli.directory1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.account.CommandKeygen
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
            CommandKeygen(),

            // Providers
            CommandRegisterProvider(),
            CommandListProviders(),
            CommandUpdateProviderName(),
            CommandProposeEnableProvider(),
            CommandProposeDisableProvider(),
            CommandTransferActionPoints(),
            CommandGetProviderInfo(),

            // Clusters
            CommandProposeClusterProvider(),
            CommandProposeClusterResourceLimits(),
            CommandProposeContainer(),
            CommandProposeContainerResourceLimits(),
            CommandProposeClusterDeployer(),

            // Nodes
            CommandAddNode(),
            CommandListProviderNodes(),
            CommandListNodes(),
            CommandGetNodeInfo(),
            CommandRemoveNode(),
            CommandGetNodeListVersion(),

            // Blockchains
            CommandProposeBlockchain(),
            CommandListBlockchainsForNode(),
            CommandListBlockchains(),
            CommandProposeConfiguration(),
            CommandGetBlockchainConfiguration(),
            CommandListBlockchainSigners(),
            // Blockchains / Governance
            CommandProposePauseBlockchain(),
            CommandProposeUnPauseBlockchain(),
            CommandProposeDeleteBlockchain(),

            // Replicas
            CommandAddBlockchainReplica(),
            CommandListBlockchainReplicas(),
            CommandRemoveBlockchainReplica(),
            CommandAddContainerReplica(),
            CommandRemoveContainerReplica(),

            // Governance
            CommandGetProposal(),
            CommandListProposalsSince(),
            CommandCreateVoterSet(),
            CommandListVoterSetMembers(),
            CommandProposeVoterSetMember(),
            CommandListVoterSets(),
            CommandProposeVoterSetGovernor(),
            CommandGetVoterSetGovernor(),
            CommandVote()

    ).associateBy { it.key() }

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}