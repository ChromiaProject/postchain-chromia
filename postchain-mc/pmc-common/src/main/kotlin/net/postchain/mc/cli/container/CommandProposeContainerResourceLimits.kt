package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.model.ContainerResourceLimitType.*
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.*

class CommandProposeContainerResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given container There are three types of limits. " +
                "Proposal can contain one, two, or all three types."
) {
    private val config by configOption()

    private val containerName by nameOption("Container name").required()

    private val _maxBlockchains by option("-mb", "--max-blockchains", help = maxBlockchainsOptionHelp).long()

    private val _cpu by option("-c", "--cpu", help = cpuOptionHelp).long()

    private val _ram by option("-r", "--ram", help = ramOptionHelp).long()

    private val _storage by option("-s", "--storage", help = storageOptionHelp).long()

    override fun run() {
        val limitsMap = mutableMapOf<ContainerResourceLimitType, Long>()
                .apply {
                    setNullable(max_blockchains, _maxBlockchains)
                    setNullable(cpu, _cpu)
                    setNullable(ram, _ram)
                    setNullable(storage, _storage)
                }

        CliExecution(config).proposeContainerLimits(containerName, limitsMap)
        println("proposal has been added successfully")
    }

    private fun MutableMap<ContainerResourceLimitType, Long>.setNullable(key: ContainerResourceLimitType, value: Long?) {
        value?.let { put(key, it) }
    }
}