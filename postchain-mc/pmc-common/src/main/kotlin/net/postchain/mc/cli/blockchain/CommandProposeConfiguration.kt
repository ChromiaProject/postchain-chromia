package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.proposal.proposeConfigurationAtOperation
import net.postchain.chain0.common.proposal.proposeConfigurationOperation
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.forceOption
import net.postchain.cli.util.heightOption
import net.postchain.common.wrap
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.readConfigurationFile

class CommandProposeConfiguration : CliktCommand(
        name = "update",
        help = """
        Propose new configuration to blockchain at specific height. 
        Height must be > current height and > all previously approved configuration heights.
        Use force flag -f to override previously added configs or to squeeze in a configuration 
        at a height < previously approved config heights. Change will be applied after voting.
        """.trimIndent()
) {
    private val client by nopClientOption()

    private val blockchainConfigFile by option("-bc", "--blockchain-config", help = "Blockchain config to propose")
            .file(mustExist = true, mustBeReadable = true, canBeDir = false)
            .required()

    private val blockchainRID by blockchainRidOption()

    private val height by heightOption()

    private val force by forceOption()

    override fun run() {
        client.transactionBuilder()
                .apply {
                    val configData = readConfigurationFile(blockchainConfigFile, null)
                    if (height == null) {
                        proposeConfigurationOperation(client.config.pubkey().wData, blockchainRID, configData.wrap())
                    } else {
                        proposeConfigurationAtOperation(client.config.pubkey().wData, blockchainRID, configData.wrap(), height!!, force)
                    }
                }
                .postSyncAwaitConfirmation()
                .printResult("Configuration was proposed",
                        "Failed to propose configuration")
    }
}
