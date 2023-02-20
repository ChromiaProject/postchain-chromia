package net.postchain.mc.cli.anchoring

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.proposal.proposeAnchoringConfigurationOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.BlockchainConfig
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeAnchoringConfiguration : CliktCommand(
        name = "update",
        help = """
        Propose new anchoring configuration. 
        """.trimIndent()
) {
    private val client by nopClientOption()

    private val anchoringConfig by option(
            "-ac",
            "--anchoring-config",
            help = "Configuration file for anchoring chain (GtvML (*.xml) or Gtv (*.gtv))"
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true).required()

    override fun run() {
        val bcConfig = BlockchainConfig.readFromFile(anchoringConfig)
        client.transactionBuilder()
                .proposeAnchoringConfigurationOperation(client.config.pubkey().data, bcConfig.data)
                .postAwaitConfirmation()
                .printResult(
                        "Anchoring configuration was proposed: ${bcConfig.hash}",
                        "Failed to propose anchoring configuration"
                )
    }
}
