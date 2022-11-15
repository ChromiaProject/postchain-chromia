package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.init.initOperation
import net.postchain.gtv.GtvNull
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.util.nopClientOption

class CommandInit : CliktCommand(
        name = "initialize",
        help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val client by nopClientOption()

    override fun run() {
        client.transactionBuilder()
                .addOperation("init", GtvNull) // TODO use code generation when it is fixed
                .postSyncAwaitConfirmation()
                .printResult(
                        "Network was initiated",
                        "Failed to initiate network"
                )
    }
}
