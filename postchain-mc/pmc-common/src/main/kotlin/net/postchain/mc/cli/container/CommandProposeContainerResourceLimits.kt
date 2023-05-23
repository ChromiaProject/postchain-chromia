package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.model.ContainerResourceLimitType.container_units
import net.postchain.chain0.model.ContainerResourceLimitType.max_blockchains
import net.postchain.chain0.proposal_container.proposal_container_limits.proposeContainerLimitsOperation
import net.postchain.chain0.version.apiVersion
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.cluster.CommandProposeClusterResourceLimits.Companion.setIfNotNull
import net.postchain.mc.cli.util.containerUnitsOption
import net.postchain.mc.cli.util.maxBlockchainsOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.compatibility.ApiCompatV2
import net.postchain.mc.compatibility.ApiCompatV2.proposeContainerLimitsOperationV2

class CommandProposeContainerResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given container. There are multiple types of limits. " +
                "Proposal can contain all types of limits or a subset of them."
) {
    private val client by nopClientOption()

    private val containerName by nameOption("Container name").required()

    private val containerUnits by containerUnitsOption()

    private val maxBlockchains by maxBlockchainsOption()

    private val description by proposalDescriptionOption()

    // Remove when api version 2 is not needed
    private val _cpu by option("-c", "--cpu", help = "CPU limit (percent of cpus, 10 == 0.1 cpu(s), 150 == 1.5 cpu(s))").long().deprecated()
    private val _ram by option("-r", "--ram", help = "RAM limit (MiB)").long().deprecated()
    private val _storage by option("-s", "--storage", help = "Storage limit (MiB)").long().deprecated()
    private val _ioRead by option("-ir", "--io-read", help = "Disk I/O read limit (MiB/s)").long().deprecated()
    private val _ioWrite by option("-iw", "--io-write", help = "Disk I/O write limit (MiB/s)").long().deprecated()

    override fun run() {
        val apiVersion = client.apiVersion()
        when {
            apiVersion >= 3 -> {
                if (containerUnits == null && maxBlockchains == null) {
                    echo("No resource limits are specified. At least one value should be specified.")
                    return
                }

                val limits = mutableMapOf<ContainerResourceLimitType, Long>()
                        .apply {
                            setIfNotNull(container_units, containerUnits)
                            setIfNotNull(max_blockchains, maxBlockchains)
                        }

                client.transactionBuilder()
                        .proposeContainerLimitsOperation(client.config.pubkey().data, containerName, limits, description)
                        .postAwaitConfirmation()
                        .printResult(
                                "Container limits proposed",
                                "Failed proposing new container limits")
            }

            else -> {
                if (maxBlockchains == null && _cpu == null && _ram == null && _storage == null && _ioRead == null && _ioWrite == null) {
                    echo("No resource limits are specified. At least one value should be specified.")
                    return
                }

                val limits = mutableMapOf<ApiCompatV2.ContainerResourceLimitType, Long>()
                        .apply {
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.max_blockchains, maxBlockchains)
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.cpu, _cpu)
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.ram, _ram)
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.storage, _storage)
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.io_read, _ioRead)
                            setIfNotNull(ApiCompatV2.ContainerResourceLimitType.io_write, _ioWrite)
                        }

                client.transactionBuilder()
                        .proposeContainerLimitsOperationV2(client.config.pubkey().data, containerName, limits, description)
                        .postAwaitConfirmation()
                        .printResult(
                                "Container limits proposed",
                                "Failed proposing new container limits")
            }
        }
    }
}