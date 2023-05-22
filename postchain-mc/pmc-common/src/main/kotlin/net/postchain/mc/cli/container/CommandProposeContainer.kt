package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.direct_container.createContainerFromOperation
import net.postchain.chain0.direct_container.createContainerFromWithUnitsOperation
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.chain0.direct_container.createContainerWithUnitsOperation
import net.postchain.chain0.version.apiVersion
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.VoterSetOrPubkeysOption
import net.postchain.mc.cli.util.containerUnitsOption
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeysOrVotersetOption


class CommandProposeContainer : CliktCommand(
        name = "add",
        help = "propose a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it."
) {
    private val client by nopClientOption()

    private val name by nameOrGenerateOption("Container name")

    private val clusterName by option(
            "-c", "--cluster",
            help = "Name of cluster to put container in. Must exist in database"
    ).required()

    private val consensus by option().flag()

    private val deployerOption by pubkeysOrVotersetOption()

    private val containerUnits by containerUnitsOption().default(1)

    override fun run() {
        val apiVersion = client.apiVersion()
        val threshold = if (consensus) -1L else 1L
        client.transactionBuilder()
                .apply {
                    when {
                        apiVersion >= 3 -> {
                            when (deployerOption) {
                                is VoterSetOrPubkeysOption.Pubkeys -> {
                                    createContainerWithUnitsOperation(
                                            client.pubkey, name, clusterName, threshold,
                                            (deployerOption as VoterSetOrPubkeysOption.Pubkeys).pubkeys,
                                            containerUnits
                                    )
                                }

                                is VoterSetOrPubkeysOption.VoterSet -> {
                                    createContainerFromWithUnitsOperation(
                                            client.pubkey,
                                            name,
                                            clusterName,
                                            threshold,
                                            (deployerOption as VoterSetOrPubkeysOption.VoterSet).data,
                                            containerUnits
                                    )
                                }
                            }
                        }

                        else -> {
                            when (deployerOption) {
                                is VoterSetOrPubkeysOption.Pubkeys -> {
                                    createContainerOperation(
                                            client.pubkey, name, clusterName, threshold,
                                            (deployerOption as VoterSetOrPubkeysOption.Pubkeys).pubkeys
                                    )
                                }

                                is VoterSetOrPubkeysOption.VoterSet -> {
                                    createContainerFromOperation(
                                            client.pubkey,
                                            name,
                                            clusterName,
                                            threshold,
                                            (deployerOption as VoterSetOrPubkeysOption.VoterSet).data
                                    )
                                }
                            }
                        }
                    }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Container $name has been created",
                        "Failed to create container"
                )
    }
}
