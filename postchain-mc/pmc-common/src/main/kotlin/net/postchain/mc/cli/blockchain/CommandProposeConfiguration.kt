package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.proposal.proposeConfigurationAtOperation
import net.postchain.chain0.proposal.proposeConfigurationOperation
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.mc.cli.AlreadyExistMode
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.blockchainRidOption
import net.postchain.mc.cli.forceOption
import net.postchain.mc.cli.heightOption
import net.postchain.mc.cli.util.BlockchainConfig
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.network.Version

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

    private val description by proposalDescriptionOption()

    override fun run() {
        val version = Version(client)
        val bcConfig = BlockchainConfig.readFromFile(blockchainConfigFile)

        client.transactionBuilder()
                .apply {
                    if (height == null) {
                        when (version.version) {
                            1L -> {
                                addOperation("propose_configuration",
                                        gtv(client.config.pubkey().data),
                                        gtv(blockchainRID),
                                        gtv(bcConfig.data)
                                )
                            }

                            else -> {
                                proposeConfigurationOperation(client.config.pubkey().data, blockchainRID, bcConfig.data, description)
                            }
                        }
                    } else {
                        when (version.version) {
                            1L -> {
                                addOperation("propose_configuration_at",
                                        gtv(client.config.pubkey().data),
                                        gtv(blockchainRID),
                                        gtv(bcConfig.data),
                                        gtv(height!!),
                                        gtv(force == AlreadyExistMode.FORCE))
                            }

                            else -> {
                                proposeConfigurationAtOperation(client.config.pubkey().data, blockchainRID, bcConfig.data, height!!, force == AlreadyExistMode.FORCE, description)
                            }
                        }
                    }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Configuration was proposed: ${bcConfig.hash}",
                        "Failed to propose configuration"
                )
    }
}
