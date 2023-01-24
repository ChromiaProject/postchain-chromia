package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.init.initOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.readConfigurationFile

class CommandInit : CliktCommand(
        name = "initialize",
        help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val client by nopClientOption()

    private val anchoringConfig by option(
            "-ac",
            "--anchoring-config",
            help = "Configuration file for anchoring chain (GtvML (*.xml) or Gtv (*.gtv))"
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)

    override fun run() {
        val anchoringConfigData = anchoringConfig?.let { readConfigurationFile(it, null).data }
        client.transactionBuilder()
                .initOperation(anchoringConfigData)
                .postAwaitConfirmation()
                .printResult(
                        "Network was initiated",
                        "Failed to initiate network"
                )
    }
}
