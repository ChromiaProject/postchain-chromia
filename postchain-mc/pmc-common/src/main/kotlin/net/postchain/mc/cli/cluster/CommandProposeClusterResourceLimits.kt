package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandProposeClusterResourceLimits : CliktCommand(
        name = "limits",
        help = "Propose new resource limits for given cluster. There are three types of limits. " +
                "Proposal can contain one, two, or all three types."
) {
    private val config by configOption()

    private val clusterName by nameOption("Cluster name").required()

    private val ram by option("-r", "--ram", help = "RAM limit").long()

    private val cpu by option("-c", "--cpu", help = "CPU limit").long()

    private val storage by option("-s", "--storage", help = "Storage limit").long() //Unit?!!
    override fun run() {
        val limitMap = mutableMapOf<String, Long>()
        ram?.let { limitMap.put("ram", it) }
        cpu?.let { limitMap.put("cpu", it) }
        storage?.let { limitMap.put("storage", it) }

        CliExecution(config).proposeClusterLimits(clusterName, limitMap)
        println("proposal has been added successfully")
    }

}