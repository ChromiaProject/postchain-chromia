package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.chain0.common.proposal.proposeProvidersOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.ProviderType
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeysOption


class CommandRegisterProvider : CliktCommand(
        name = "register",
        help = """Register new provider with given pubkey. There are three tiers of providers:
        ```
        - Community Node Provider: Basic provider, can deploy dapps and add nodes that replicates blockchains (replica) (default)
        - Node Provider:           Can add block builder nodes
        - System Provider:         System level permissions and can add node to the system cluster
        ```
    """
) {
    private val client by nopClientOption()

    private val pubkeys by pubkeysOption("Comma delimited list of public keys to register as providers").required()

    private val providerTier by mutuallyExclusiveOptions(
            option("-cnp", help = "community node provider").flag().convert { ProviderType.COMMUNITY_NODE_PROVIDER },
            option("-np", help = "node provider").flag().convert { ProviderType.NODE_PROVIDER },
            option("-sp", help = "system provider").flag().convert { ProviderType.SYSTEM_PROVIDER },
            name = "Provider tier",
    ).required()

    private val enable by mutuallyExclusiveOptions(
            option("--enable", "-e", help = "enable provider").flag().convert { true },
            option("--disable", "-d", help = "disable provider").flag().convert { false },
            name = "Provider state",
    ).required()

    private val batch by option(help = "Allows to add a batch of providers (comma delimited list of public keys)").flag()

    override fun run() {
        if (batch) {
            client.transactionBuilder()
                    .proposeProvidersOperation(client.pubkey,
                            pubkeys.map { it.data }, providerTier.toTier(), providerTier.isSystem(), enable
                    )
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider batch has been proposed",
                            "Failed to propose provider batch"
                    )
        } else {
            if (pubkeys.size != 1) {
                println("Use --batch mode to add multiple providers")
                return
            }

            client.transactionBuilder()
                    .registerProviderOperation(client.pubkey, pubkeys.first(), providerTier.toTier())
                    .apply {
                        if (providerTier.shouldEnable(enable)) proposeProviderStateOperation(client.pubkey, pubkeys.first().data, enable)
                        if (providerTier == ProviderType.SYSTEM_PROVIDER) proposeProviderIsSystemOperation(client.pubkey, pubkeys.first().data, true)
                    }
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider has been added ${enable.let { if (it) "and proposed for enabling " else "" }}",
                            "Failed to add provider"
                    )
        }
    }
}
