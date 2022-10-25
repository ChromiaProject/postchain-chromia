package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.model.ClusterResourceLimitType
import net.postchain.chain0.model.ClusterResourceLimitType.*
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.*

class CommandProposeClusterResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given cluster. There are three types of limits. " +
                "Proposal can contain one, two, or all three types."
) {
    private val config by configOption()

    private val clusterName by nameOption("Cluster name").required()

    private val _maxContainers by option("-mc", "--max-containers", help = "Max containers per cluster").long()

    private val _maxDapps by option("-md", "--max-dapps", help = maxDappsOptionHelp).long()

    private val _cpu by option("-c", "--cpu", help = cpuOptionHelp).long()

    private val _ram by option("-r", "--ram", help = ramOptionHelp).long()

    private val _storage by option("-s", "--storage", help = storageOptionHelp).long()

    override fun run() {
        val limitsMap = mutableMapOf<ClusterResourceLimitType, Long>()
                .apply {
                    setNullable(max_containers, _maxContainers)
                    setNullable(default_container_max_dapps, _maxDapps)
                    setNullable(default_container_cpu, _cpu)
                    setNullable(default_container_ram, _ram)
                    setNullable(default_container_storage, _storage)
                }

        CliExecution(config).proposeClusterLimits(clusterName, limitsMap)
        println("proposal has been added successfully")
    }

    private fun MutableMap<ClusterResourceLimitType, Long>.setNullable(key: ClusterResourceLimitType, value: Long?) {
        value?.let { put(key, it) }
    }
}