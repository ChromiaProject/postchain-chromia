package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.ProviderType
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalMessageOption
import net.postchain.mc.cli.util.providerTierOption
import net.postchain.mc.cli.util.pubkeyOption


class CommandRegisterProvider : CliktCommand(
        name = "add",
        help = """Register new provider with given pubkey. There are three tiers of providers:
        ```
        - Community Node Provider: Basic provider, can deploy dapps and add nodes that replicates blockchains (replica) (default)
        - Node Provider:           Can add block builder nodes
        - System Provider:         System level permissions and can add node to the system cluster
        ```
    """
) {
    private val client by nopClientOption()

    private val pubkey by pubkeyOption("Public key to register as provider").required()

    private val providerTier by providerTierOption()

    private val enable by option(help = "Adds a proposal to enable this provider (only needed for node providers)").flag()

    private val message by proposalMessageOption()

    override fun run() {
        client.transactionBuilder()
                .registerProviderOperation(client.pubkey, pubkey, providerTier.toTier())
                .apply {
                    if (providerTier.shouldEnable(enable)) proposeProviderStateOperation(client.pubkey, pubkey.data, enable, message)
                    if (providerTier == ProviderType.SYSTEM_PROVIDER) proposeProviderIsSystemOperation(client.pubkey, pubkey.data, true, message)
                }
                .postAwaitConfirmation()
                .printResult(
                        "Provider has been added ${enable.let { if (it) "and proposed for enabling " else "" }}",
                        "Failed to add provider"
                )
    }
}
