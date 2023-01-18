package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.proposal.proposeContainerLimitsOperation
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.cluster.CommandProposeClusterResourceLimits.Companion.setIfNotNull
import net.postchain.mc.cli.util.cpuOptionHelp
import net.postchain.mc.cli.util.maxBlockchainsOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.cli.util.ramOptionHelp
import net.postchain.mc.cli.util.storageOptionHelp

class CommandProposeContainerResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given container There are three types of limits. " +
                "Proposal can contain one, two, or all three types."
) {
    private val client by nopClientOption()

    private val containerName by nameOption("Container name").required()

    private val _maxBlockchains by maxBlockchainsOption()

    private val _cpu by option("-c", "--cpu", help = cpuOptionHelp).long()

    private val _ram by option("-r", "--ram", help = ramOptionHelp).long()

    private val _storage by option("-s", "--storage", help = storageOptionHelp).long()

    private val description by proposalDescriptionOption()

    override fun run() {
        if (_maxBlockchains == null && _cpu == null && _ram == null && _storage == null) {
            println("No resource limits are specified. At least one value should be specified.")
            return
        }

        val limits = mutableMapOf<ContainerResourceLimitType, Long>()
                .apply {
                    setIfNotNull(max_blockchains, _maxBlockchains)
                    setIfNotNull(cpu, _cpu)
                    setIfNotNull(ram, _ram)
                    setIfNotNull(storage, _storage)
                }

        client.transactionBuilder()
                .proposeContainerLimitsOperation(client.config.pubkey().data, containerName, limits, description)
                .postAwaitConfirmation()
                .printResult(
                        "Container limits proposed",
                        "Failed proposing new container limits")
    }
}