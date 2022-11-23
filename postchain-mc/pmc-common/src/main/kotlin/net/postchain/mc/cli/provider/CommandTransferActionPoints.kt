package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.transferActionPointsOperation
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption

class CommandTransferActionPoints : CliktCommand(
        name = "transfer-action-points",
        help = "transfer some of your action points to another provider"
) {
    private val client by nopClientOption()

    private val pubkey by requiredPubkeyOption()

    private val amount by option("-a", "--amount", help = "number of points to transfer").long().required()

    override fun run() {
        client.transactionBuilder()
                .transferActionPointsOperation(client.config.pubkey().data, pubkey.hexStringToByteArray(), amount)
                .postAwaitConfirmation()
                .printResult(
                        "Action points transferred",
                        "Transferring action points failed"
                )
    }
}