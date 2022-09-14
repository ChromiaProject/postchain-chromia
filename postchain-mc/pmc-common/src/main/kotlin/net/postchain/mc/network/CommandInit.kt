package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.directory1.initOperation
import net.postchain.cli.util.hostOption
import net.postchain.cli.util.portOption
import net.postchain.mc.cli.util.nopClientOption

class CommandInit : CliktCommand(
    name = "initialize",
    help = "Create system cluster with naked system container for the directory blockchain. Module argument initial_provider becomes first member of SYSTEM_P voter set."
) {

    private val client by nopClientOption()

    private val host by hostOption().required()

    private val port by portOption().required()

    override fun run() {
        client.transactionBuilder()
            .initOperation(host, port.toLong())
            .postSyncAwaitConfirmation()
    }
}