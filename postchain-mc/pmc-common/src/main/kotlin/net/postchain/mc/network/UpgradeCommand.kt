package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.proposal.proposeNetworkUpgradeOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.BlockchainConfig
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption

class UpgradeCommand : CliktCommand(
        help = "Upgrade the management chain"
) {

    private val client by nopClientOption()

    private val blockchainConfigFile by option("-bc", "--blockchain-config", help = "Blockchain config to propose")
            .file(mustExist = true, mustBeReadable = true, canBeDir = false)
            .required()

    private val description by proposalDescriptionOption()
    override fun run() {
        val bcConfig = BlockchainConfig.readFromFile(blockchainConfigFile)

        client.transactionBuilder()
                .proposeNetworkUpgradeOperation(client.config.pubkey().data, bcConfig.data, description)
                .postAwaitConfirmation()
                .printResult(
                        "Network was initiated",
                        "Failed to initiate network"
                )
    }
}
