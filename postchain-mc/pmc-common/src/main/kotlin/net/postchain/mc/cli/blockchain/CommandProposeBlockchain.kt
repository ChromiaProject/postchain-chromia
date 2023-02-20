package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeBlockchainOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.blockchainConfigOption
import net.postchain.mc.cli.util.BlockchainConfig
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.nopClientOption

class CommandProposeBlockchain : CliktCommand(
        name = "add",
        help = "propose a new blockchain in a specific container. Change will be applied after voting within the deployer voter set of the cluster that the container belongs to."
) {
    private val client by nopClientOption()

    private val blockchainConfigFile by blockchainConfigOption().required()

    private val container by option("-c", "--container", help = "Name of container to run in").required()

    private val name by nameOption("Name of blockchain").required()

    private val quiet by option("-q", "--quiet", help = "Only prints Blockchain RID if succeeds").flag()

    override fun run() {
        val bcConfig = BlockchainConfig.readFromFile(blockchainConfigFile)
        client.transactionBuilder()
                .proposeBlockchainOperation(client.pubkey, bcConfig.data, name, container, "")
                .postAwaitConfirmation()
                .apply {
                    if (quiet) {
                        printResult(bcConfig.hash.toString(), "")
                    } else {
                        printResult(
                                "Blockchain $name has been proposed: ${bcConfig.hash}",
                                "Cannot add bc proposal"
                        )
                    }
                }
    }
}
