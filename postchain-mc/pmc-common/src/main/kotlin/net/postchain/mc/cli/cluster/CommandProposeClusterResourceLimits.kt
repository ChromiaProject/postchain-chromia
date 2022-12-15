package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.proposal.proposeClusterLimitsOperation
import net.postchain.chain0.model.ClusterResourceLimitType
import net.postchain.chain0.model.ClusterResourceLimitType.*
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.*

class CommandProposeClusterResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given cluster. There are three types of limits. " +
                "Proposal can contain one, two, or all three types."
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

    override fun run() {
        val limits = mutableMapOf<ClusterResourceLimitType, Long>()
                .apply {
                    setIfNotNull(max_containers, _maxContainers)
                    setIfNotNull(default_container_max_blockchains, _maxBlockchains)
                    setIfNotNull(default_container_cpu, _cpu)
                    setIfNotNull(default_container_ram, _ram)
                    setIfNotNull(default_container_storage, _storage)
                }

        client.transactionBuilder()
                .proposeClusterLimitsOperation(client.config.pubkey().data, clusterName, limits)
                .postAwaitConfirmation()
                .printResult(
                        "Cluster limits proposed",
                        "Failed proposing new cluster limits")
    }
}