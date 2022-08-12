package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.proposeBlockchainOperation
import net.postchain.cli.util.blockchainConfigOption
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.ConfigFormat
import net.postchain.mc.cli.base.createProposal
import net.postchain.mc.cli.base.readConfigurationFile
import net.postchain.mc.cli.util.configOption
import java.io.File

class CommandProposeBlockchain : CliktCommand(
    name = "add",
    help = "propose a new blockchain in a specific container. Change will be applied after voting within the deployer voter set of the cluster that the container belongs to."
) {
    private val config by configOption()

    private val blockchainConfigFile by blockchainConfigOption()

    private val container by option("-c", "--container", help = "Name of container to run in").required()

    override fun run() {
        val bcConfig = File(blockchainConfigFile)
        val format = if (bcConfig.extension == "xml") ConfigFormat.XML else ConfigFormat.GTV
        ClientUtil.fromConfig(config).createProposal(
            ProposalType.bc,
            config.pubKey,
            ClientUtil.sigMaker(config)
        ) {
            proposeBlockchainOperation(
                config.pubKey.hexStringToByteArray(),
                readConfigurationFile(bcConfig, format),
                container
            )
        }
    }
}
