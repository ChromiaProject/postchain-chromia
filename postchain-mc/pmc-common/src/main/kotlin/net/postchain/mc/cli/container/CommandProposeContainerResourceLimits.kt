package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.model.ContainerResourceLimitType.cpu
import net.postchain.chain0.model.ContainerResourceLimitType.io_read
import net.postchain.chain0.model.ContainerResourceLimitType.io_write
import net.postchain.chain0.model.ContainerResourceLimitType.max_blockchains
import net.postchain.chain0.model.ContainerResourceLimitType.ram
import net.postchain.chain0.model.ContainerResourceLimitType.storage
import net.postchain.chain0.proposal.proposeContainerLimitsOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.cluster.CommandProposeClusterResourceLimits.Companion.setIfNotNull
import net.postchain.mc.cli.util.cpuOptionHelp
import net.postchain.mc.cli.util.ioReadOptionHelp
import net.postchain.mc.cli.util.ioWriteOptionHelp
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

    private val _ioRead by option("-ir", "--io-read", help = ioReadOptionHelp).long()

    private val _ioWrite by option("-iw", "--io-write", help = ioWriteOptionHelp).long()

    private val description by proposalDescriptionOption()

    override fun run() {
        if (_maxBlockchains == null && _cpu == null && _ram == null && _storage == null && _ioRead == null && _ioWrite == null) {
            echo("No resource limits are specified. At least one value should be specified.")
            return
        }

        val limits = mutableMapOf<ContainerResourceLimitType, Long>()
                .apply {
                    setIfNotNull(max_blockchains, _maxBlockchains)
                    setIfNotNull(cpu, _cpu)
                    setIfNotNull(ram, _ram)
                    setIfNotNull(storage, _storage)
                    setIfNotNull(io_read, _ioRead)
                    setIfNotNull(io_write, _ioWrite)
                }

        client.transactionBuilder()
                .proposeContainerLimitsOperation(client.config.pubkey().data, containerName, limits, description)
                .postAwaitConfirmation()
                .printResult(
                        "Container limits proposed",
                        "Failed proposing new container limits")
    }
}