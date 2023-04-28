package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.direct_cluster.createClusterFromOperation
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.VoterSetOrPubkeysOption
import net.postchain.mc.cli.util.nameOrGenerateOption
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeysOrVotersetOption

class CommandAddCluster : CliktCommand(
        name = "add",
        help = "Create a new cluster that can hold containers with blockchains."
) {

    private val client by nopClientOption()

    private val name by nameOrGenerateOption("Cluster name")

    private val providerOptions by pubkeysOrVotersetOption()


    private val governorName by option(
            "-g", "--governor",
            help = "Name of another voter set which can update this cluster."
    ).required()

    override fun run() {
        client.transactionBuilder()
                .apply {
                    when (providerOptions) {
                        is VoterSetOrPubkeysOption.Pubkeys -> createClusterOperation(client.pubkey, name, governorName, (providerOptions as VoterSetOrPubkeysOption.Pubkeys).pubkeys)
                        is VoterSetOrPubkeysOption.VoterSet -> createClusterFromOperation(client.pubkey, name, governorName, (providerOptions as VoterSetOrPubkeysOption.VoterSet).data)
                    }
                }
                .postAwaitConfirmation()
                .printResult(
                        "Cluster $name added",
                        "Could not create cluster"
                )
    }
}

