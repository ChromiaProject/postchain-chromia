package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.proposal_cluster.proposeClusterLimitsOperation
import net.postchain.chain0.version.apiVersion
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.clusterUnitsOption
import net.postchain.mc.cli.util.maxBlockchainsOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.compatibility.ApiCompatV2
import net.postchain.mc.compatibility.ApiCompatV2.proposeClusterLimitsOperationV2

class CommandProposeClusterResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given cluster."
) {
    companion object {
        fun <K> MutableMap<K, Long>.setIfNotNull(key: K, value: Long?) {
            value?.let { put(key, it) }
        }
    }

    private val client by nopClientOption()

    private val clusterName by nameOption("Cluster name").required()

    private val clusterUnits by clusterUnitsOption()

    private val description by proposalDescriptionOption()

    // Remove when api version 2 is not needed
    private val _maxContainers by option("-mc", "--max-containers", help = "Max containers per cluster").long().deprecated()
    private val _maxBlockchains by maxBlockchainsOption().deprecated()
    private val _cpu by option("-c", "--cpu", help = "CPU limit (percent of cpus, 10 == 0.1 cpu(s), 150 == 1.5 cpu(s))").long().deprecated()
    private val _ram by option("-r", "--ram", help = "RAM limit (MiB)").long().deprecated()
    private val _storage by option("-s", "--storage", help = "Storage limit (MiB)").long().deprecated()
    private val _ioRead by option("-ir", "--io-read", help = "Disk I/O read limit (MiB/s)").long().deprecated()
    private val _ioWrite by option("-iw", "--io-write", help = "Disk I/O write limit (MiB/s)").long().deprecated()

    override fun run() {
        val apiVersion = client.apiVersion()
        client.transactionBuilder()
                .apply {
                    when {
                        apiVersion >= 3 -> proposeClusterLimitsOperation(client.config.pubkey().data, clusterName, clusterUnits, description)
                        else -> {
                            val limits = mutableMapOf<ApiCompatV2.ClusterResourceLimitType, Long>()
                                    .apply {
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.max_containers, _maxContainers)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_max_blockchains, _maxBlockchains)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_cpu, _cpu)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_ram, _ram)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_storage, _storage)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_io_read, _ioRead)
                                        setIfNotNull(ApiCompatV2.ClusterResourceLimitType.default_container_io_write, _ioWrite)
                                    }
                            proposeClusterLimitsOperationV2(client.config.pubkey().data, clusterName, limits, description)

                        }
                    }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Cluster limits proposed",
                        "Failed proposing new cluster limits")
    }
}