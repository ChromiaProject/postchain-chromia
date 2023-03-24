package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.model.ClusterResourceLimitType
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_cpu
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_io_read
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_io_write
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_max_blockchains
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_ram
import net.postchain.chain0.model.ClusterResourceLimitType.default_container_storage
import net.postchain.chain0.model.ClusterResourceLimitType.max_containers
import net.postchain.chain0.proposal.proposeClusterLimitsOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.cpuOptionHelp
import net.postchain.mc.cli.util.ioReadOptionHelp
import net.postchain.mc.cli.util.ioWriteOptionHelp
import net.postchain.mc.cli.util.maxBlockchainsOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.cli.util.ramOptionHelp
import net.postchain.mc.cli.util.storageOptionHelp

class CommandProposeClusterResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given cluster. There are multiple types of limits. " +
                "Proposal can contain all types of limits or a subset of them."
) {
    companion object {
        fun <K> MutableMap<K, Long>.setIfNotNull(key: K, value: Long?) {
            value?.let { put(key, it) }
        }
    }

    private val client by nopClientOption()

    private val clusterName by nameOption("Cluster name").required()

    private val _maxContainers by option("-mc", "--max-containers", help = "Max containers per cluster").long()

    private val _maxBlockchains by maxBlockchainsOption()

    private val _cpu by option("-c", "--cpu", help = cpuOptionHelp).long()

    private val _ram by option("-r", "--ram", help = ramOptionHelp).long()

    private val _storage by option("-s", "--storage", help = storageOptionHelp).long()

    private val _ioRead by option("-ir", "--io-read", help = ioReadOptionHelp).long()

    private val _ioWrite by option("-iw", "--io-write", help = ioWriteOptionHelp).long()

    private val description by proposalDescriptionOption()

    override fun run() {
        val limits = mutableMapOf<ClusterResourceLimitType, Long>()
                .apply {
                    setIfNotNull(max_containers, _maxContainers)
                    setIfNotNull(default_container_max_blockchains, _maxBlockchains)
                    setIfNotNull(default_container_cpu, _cpu)
                    setIfNotNull(default_container_ram, _ram)
                    setIfNotNull(default_container_storage, _storage)
                    setIfNotNull(default_container_io_read, _ioRead)
                    setIfNotNull(default_container_io_write, _ioWrite)
                }

        client.transactionBuilder()
                .proposeClusterLimitsOperation(client.config.pubkey().data, clusterName, limits, description)
                .postAwaitConfirmation()
                .printResult(
                        "Cluster limits proposed",
                        "Failed proposing new cluster limits")
    }
}