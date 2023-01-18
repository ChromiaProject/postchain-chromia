package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.chain0.common.proposal.ProviderInfo
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.chain0.common.proposal.proposeProvidersOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.parse.GtvParser
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.PropertiesConfigurationValueSource
import net.postchain.mc.cli.util.ProviderType
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.cli.util.pubkeysOption


class CommandRegisterProvider : CliktCommand(
        name = "register",
        help = """Register new provider with given pubkey. There are three tiers of providers:
        ```
        - Community Node Provider: Basic provider, can deploy dapps and add nodes that replicates blockchains (replica) (default)
        - Node Provider:           Can add block builder nodes
        - System Provider:         System level permissions and can add node to the system cluster
        ```
        
        Examples:
        ```
        (1): pmc provider add --batch -cnp --enable --provider '{pubkey=x"aa...",name="foo",url="http://foo/api"}' --provider '{pubkey=x"bb...",name="bar",url="http://bar/api"}'
        ```
        ```
        (2): pmc provider add --batch -cnp --enable, where providers will be load from `providers.properties` file:
                provider={pubkey=x"aa...",name="foo",url="http://foo/api"};{pubkey=x"bb...",name="bar",url="http://bar/api"}
                provider={pubkey=x"cc...",name="foobar",url="http://foobar/api"}
        ```
    """
) {
    init {
        context {
            valueSource = PropertiesConfigurationValueSource.from("providers.properties")
        }
    }

    private val client by nopClientOption()

    private val pubkeys by pubkeysOption("Comma delimited list of public keys to register as providers")
            .default(emptyList())
            .deprecated("Use --provider option instead")

    private val provider by option(
            help = "Multiple objects to register as providers (see examples)",
            valueSourceKey = "provider"
    ).convert {
        GtvObjectMapper.fromGtv(GtvParser.parse(it), ProviderInfo::class)
    }.multiple(required = true)

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

    private val batch by option(help = "Allows to add a batch of providers (comma delimited list of objects, see examples)").flag()

    private val description by proposalDescriptionOption()

    override fun run() {
        if (batch) {
            client.transactionBuilder()
                    .proposeProvidersOperation(
                            client.pubkey, provider, providerTier.toTier(), providerTier.isSystem(), enable, description
                    )
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider batch has been proposed",
                            "Failed to propose provider batch"
                    )
        } else {
            when {
                pubkeys.isEmpty() -> throw CliktError("--pubkeys must be provided")
                pubkeys.size != 1 -> throw CliktError("Use --batch mode to add multiple providers")
            }
            client.transactionBuilder()
                    .registerProviderOperation(client.pubkey, pubkeys.first(), providerTier.toTier())
                    .apply {
                        if (providerTier.shouldEnable(enable)) proposeProviderStateOperation(client.pubkey, pubkeys.first().data, enable, description)
                        if (providerTier == ProviderType.SYSTEM_PROVIDER) proposeProviderIsSystemOperation(client.pubkey, pubkeys.first().data, true, description)
                    }
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider has been added ${enable.let { if (it) "and proposed for enabling " else "" }}",
                            "Failed to add provider"
                    )
        }
    }
}
