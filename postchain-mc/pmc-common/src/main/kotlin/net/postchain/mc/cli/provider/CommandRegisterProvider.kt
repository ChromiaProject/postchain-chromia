package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.chain0.common.proposal.ProviderInfo
import net.postchain.chain0.common.proposal.proposeProviderIsSystemOperation
import net.postchain.chain0.common.proposal.proposeProviderStateOperation
import net.postchain.chain0.common.proposal.proposeProvidersOperation
import net.postchain.chain0.common.registerProviderOperation
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.parse.GtvParser
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.PropertiesConfigurationValueSource
import net.postchain.mc.cli.util.ProviderType
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.proposalDescriptionOption
import net.postchain.mc.cli.util.pubkeyOption

class BatchOptions : OptionGroup() {

    val batch by option(help = "Allows to add a batch of providers (comma delimited list of objects, see examples)").flag()
    val provider by option(
            help = "Multiple objects to register as providers (see examples)",
            valueSourceKey = "provider"
    ).convert {
        val pi = GtvParser.parse(it).asDict().toMutableMap()
        pi.putIfAbsent("name", gtv(""))
        pi.putIfAbsent("url", gtv(""))
        GtvObjectMapper.fromGtv(gtv(pi), ProviderInfo::class)
    }.multiple(required = true)
}

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
        (1): pmc provider add -cnp --enable --pubkey aa...
        ```
        ```
        (2): pmc provider add --batch -cnp --enable --provider '{pubkey=x"aa...",name="foo",url="http://foo/api"}' --provider '{pubkey=x"bb...",name="bar"}'
        ```
        ```
        (3): pmc provider add --batch -cnp --enable, where providers will be load from `providers.properties` file:
                provider={pubkey=x"aa...",name="foo",url="http://foo/api"};{pubkey=x"bb...",name="bar",url="http://bar/api"}
                provider={pubkey=x"cc...",url="http://foobar/api"}
        ```
    """
) {
    init {
        context {
            valueSource = PropertiesConfigurationValueSource.from("providers.properties")
        }
    }

    private val client by nopClientOption()

    private val pubkey by pubkeyOption("Public key to register as provider")

    private val batchOptions by BatchOptions().cooccurring()

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


    private val description by proposalDescriptionOption()

    override fun run() {
        if (batchOptions != null) {
            if (pubkey != null) throw CliktError("use --provider instead of --pubkey in a batch mode")
            client.transactionBuilder()
                    .proposeProvidersOperation(
                            client.pubkey, batchOptions!!.provider, providerTier.toTier(), providerTier.isSystem(), enable, description
                    )
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider batch has been proposed",
                            "Failed to propose provider batch"
                    )
        } else {
            if (pubkey == null) throw CliktError("--pubkey must be provided")
            client.transactionBuilder()
                    .registerProviderOperation(client.pubkey, pubkey!!, providerTier.toTier())
                    .apply {
                        if (providerTier.shouldEnable(enable)) proposeProviderStateOperation(client.pubkey, pubkey!!.data, enable, description)
                        if (providerTier == ProviderType.SYSTEM_PROVIDER) proposeProviderIsSystemOperation(client.pubkey, pubkey!!.data, true, description)
                    }
                    .postAwaitConfirmation()
                    .printResult(
                            "Provider has been added ${enable.let { if (it) "and proposed for enabling " else "" }}",
                            "Failed to add provider"
                    )
        }
    }
}
