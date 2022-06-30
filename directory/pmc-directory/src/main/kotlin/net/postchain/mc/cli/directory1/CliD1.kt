package net.postchain.mc.cli.directory1

import com.beust.jcommander.JCommander
import net.postchain.mc.cli.account.CommandKeygen
import net.postchain.mc.cli.base.CliBase
import net.postchain.mc.cli.base.Command
import net.postchain.mc.cli.provider.*
import net.postchain.mc.cli.replica.*
import net.postchain.mc.cli.votingupdates.*

class CliD1: CliBase() {
    override val commands: Map<String, Command> = listOf(
            CommandKeygen(),

            // Clusters
            CommandProposeClusterProvider(),
            CommandProposeClusterResourceLimits(),
            CommandProposeClusterDeployer(),

            // Blockchains
            CommandProposeConfiguration(),

            // Replicas
            CommandAddBlockchainReplica(),
            CommandRemoveBlockchainReplica(),

            // Governance
            CommandCreateVoterSet(),
            CommandListVoterSetMembers(),
            CommandProposeVoterSetMember(),
            CommandListVoterSets(),
            CommandProposeVoterSetGovernor(),
            CommandGetVoterSetGovernor(),

    ).associateBy { it.key() }

    init {
        jCommander = with(JCommander.newBuilder()) {
            commands.forEach { (key, command) -> addCommand(key, command) }
            build()
        }
    }
}