package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.chain0.common.proposal.proposeProviderQuotaOperation
import net.postchain.mc.cli.base.printResult
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.nopClientOption
import net.postchain.mc.cli.util.providerQuotaTypeOption
import net.postchain.mc.cli.util.providerTierOption

class CommandProposeProviderQuota : CliktCommand(
        name = "quota",
        help = "Propose provider quota"
) {
    private val client by nopClientOption()
    private val providerTier by providerTierOption().required()
    private val providerQuotaType by providerQuotaTypeOption().required()
    private val value by option("-v", "--value", help = "quota value").long().required()

    override fun run() {
        client.transactionBuilder()
                .proposeProviderQuotaOperation(client.config.pubkey().data, providerTier.toTier(), providerQuotaType, value)
                .postAwaitConfirmation()
                .printResult(
                        "Provider quota value has been proposed",
                        "Cannot propose a provider quota value"
                )
    }
}