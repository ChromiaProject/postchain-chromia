package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.init.initOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.util.BlockchainConfig
import net.postchain.mc.cli.util.nopClientOption

class CommandInit : CliktCommand(
        name = "initialize",
        help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val client by nopClientOption()

    private val systemAnchoringConfig by option(
            "-sac",
            "--system-anchoring-config",
            help = "Configuration file for system anchoring chain (GtvML (*.xml) or Gtv (*.gtv))"
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

    private val clusterAnchoringConfig by option(
            "-cac",
            "--cluster-anchoring-config",
            help = "Configuration file for cluster anchoring chain (GtvML (*.xml) or Gtv (*.gtv))"
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

    override fun run() {
        if (systemAnchoringConfig != null && clusterAnchoringConfig == null) {
            echo("System anchoring requires cluster anchoring. Please specify a cluster anchoring configuration.")
            return
        }

        val systemAnchoringConfigData = systemAnchoringConfig?.let { BlockchainConfig.readFromFile(it).data }
        val clusterAnchoringConfigData = clusterAnchoringConfig?.let { BlockchainConfig.readFromFile(it).data }

        client.transactionBuilder()
                .initOperation(systemAnchoringConfigData, clusterAnchoringConfigData)
                .postAwaitConfirmation()
                .printResult(
                        "Network was initiated",
                        "Failed to initiate network"
                )
    }
}
