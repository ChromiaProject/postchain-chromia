package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.*
import net.postchain.chain0.common.proposal.proposeEnableProviderOperation
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.chain0.model.ProviderTier
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.pubkeyOption


enum class ProviderType {
    COMMUNITY_NODE_PROVIDER,
    NODE_PROVIDER,
    SYSTEM_PROVIDER;

    fun toTier() = when (this) {
        COMMUNITY_NODE_PROVIDER -> ProviderTier.COMMUNITY_NODE_PROVIDER
        else -> ProviderTier.NODE_PROVIDER
    }

    fun shouldEnable(enable: Boolean) = enable && this == NODE_PROVIDER
}

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
    init {
    }
    private val client by nopClientOption()
    private val pubkey by pubkeyOption("Public key to register as provider").required()

    private val providerTier by option(help = "Provider Tier (default: -cnp)").switch(
            "-cnp" to ProviderType.COMMUNITY_NODE_PROVIDER,
            "-np" to ProviderType.NODE_PROVIDER,
            "-sp" to ProviderType.SYSTEM_PROVIDER
    ).default(ProviderType.COMMUNITY_NODE_PROVIDER)

    private val enable by option(help = "Adds a proposal to enable this provider (only needed for node providers)").flag()

    override fun run() {
        client.transactionBuilder()
                .registerProviderOperation(client.pubkey, pubkey, providerTier.toTier())
                .apply {
                    if (providerTier.shouldEnable(enable)) proposeEnableProviderOperation(client.pubkey, pubkey.data)
                    if (providerTier == ProviderType.SYSTEM_PROVIDER) proposeProviderIsSystemOperation(client.pubkey, pubkey.data, true)
                }
                .postSyncAwaitConfirmation()
                .printResult(
                        "Provider has been added ${enable.let { if (it) "and proposed for enabling " else "" }}",
                        "Failed to add provider"
                )
    }
}
