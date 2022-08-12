package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.proposal.ProposalType
import net.postchain.chain0.common.proposal.proposeConfigurationOperation
import net.postchain.chain0.common.queries.getBlockchain
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.chain0.common.queries.getProvider
import net.postchain.cli.util.*
import net.postchain.common.hexStringToByteArray
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.ConfigFormat
import net.postchain.mc.cli.base.createProposal
import net.postchain.mc.cli.base.readConfigurationFile
import net.postchain.mc.cli.util.configOption
import java.io.File

class CommandProposeConfiguration : CliktCommand(
    name = "update",
    help = """
        Propose new configuration to blockchain at specific height. 
        Height must be > current height and > all previously approved configuration heights.
        Use force flag -f to override previously added configs or to squeeze in a configuration 
        at a height < previously approved config heights. Change will be applied after voting.
        """.trimIndent()
) {
    private val config by configOption()

    private val blockchainConfigFile by blockchainConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val height by heightOption()

    private val force by forceOption()

    override fun run() {
        val bcConfig = File(blockchainConfigFile)
        val format = if (bcConfig.extension == "xml") ConfigFormat.XML else ConfigFormat.GTV
        val client = ClientUtil.fromConfig(config)
        val proposalId = client.createProposal(
            ProposalType.conf,
            config.pubKey,
            ClientUtil.sigMaker(config)
        ) {
            proposeConfigurationOperation(
                blockchainRID.data,
                config.pubKey.hexStringToByteArray(),
                readConfigurationFile(bcConfig, format),
                height ?: (client.getBlockchainLastHeight(blockchainRID.data) + 5),
                force
            )
        }
        println("Proposal for updating configuration of bc $blockchainRID created ${proposalId.let { if (it == null) "" else "with id: $it" }}")
    }
}