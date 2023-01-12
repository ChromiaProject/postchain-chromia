package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeProvidersBatchOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.ProviderType
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeysOption


class CommandAddBatchProvider : CliktCommand(
        name = "add-batch",
        help = """Propose providers with given pubkeys, initial state (enabled/disabled), and tier. There are three tiers of providers:
        ```
        - Community Node Provider: Basic provider, can deploy dapps and add nodes that replicates blockchains (replica) (default)
        - Node Provider:           Can add block builder nodes
        - System Provider:         System level permissions and can add node to the system cluster
        ```
    """
) {
    private val client by nopClientOption()

    private val pubkeys by pubkeysOption("Public key to register as provider").required()

    private val providerTier by mutuallyExclusiveOptions(
            option("-cnp", help = "community node provider").flag().convert { ProviderType.COMMUNITY_NODE_PROVIDER },
            option("-np", help = "node provider").flag().convert { ProviderType.NODE_PROVIDER },
            option("-snp", help = "system provider").flag().convert { ProviderType.SYSTEM_PROVIDER },
            name = "Provider tier",
    ).required()

    private val enable by mutuallyExclusiveOptions(
            option("--enabled", "-e", help = "enable provider").flag().convert { true },
            option("--disabled", "-d", help = "disable provider").flag().convert { false },
            name = "Provider state",
    ).required()

    override fun run() {
        client.transactionBuilder()
                .proposeProvidersBatchOperation(client.pubkey,
                        pubkeys.joinToString(",") { it.hex() }, providerTier.toTier(), providerTier.isSystem(), enable
                )
                .postAwaitConfirmation()
                .printResult(
                        "Provider batch has been proposed",
                        "Failed to propose provider batch"
                )
    }
}
