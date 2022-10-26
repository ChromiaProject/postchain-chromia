package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.chain0.chromia1.updateProviderOperation
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption

class CommandUpdateProvider : CliktCommand(
        name = "update",
        help = "update provider information"
) {
    private val client by nopClientOption()

    private val name by nameOption("Provider name")

    private val beneficiary by option("-b", "--beneficiary", help = "Beneficiary account").convert { it.hexStringToWrappedByteArray() }

    override fun run() {
        client.transactionBuilder()
                .updateProviderOperation(client.config.pubkey().wData, name, beneficiary)
                .postSyncAwaitConfirmation()
                .printResult("Information updated",
                        "Could not update provider data")
    }
}
