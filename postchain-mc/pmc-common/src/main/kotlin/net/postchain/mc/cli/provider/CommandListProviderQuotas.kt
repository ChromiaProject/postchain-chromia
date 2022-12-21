package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getProviderQuotas
import net.postchain.chain0.model.ProviderQuotaType
import net.postchain.chain0.model.ProviderTier.COMMUNITY_NODE_PROVIDER
import net.postchain.chain0.model.ProviderTier.NODE_PROVIDER
import net.postchain.mc.cli.util.clientOption

class CommandListProviderQuotas : CliktCommand(
        name = "quotas",
        help = "List provider quotas"
) {
    private val client by clientOption()

    override fun run() {
        val quotas = client.getProviderQuotas().associate {
            (it.providerQuotaType to it.tier) to it.value
        }

        table {
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
            header("Quotas", NODE_PROVIDER.name, COMMUNITY_NODE_PROVIDER.name)
            ProviderQuotaType.values().forEach {
                row(
                        it.name,
                        quotas[it to NODE_PROVIDER]?.toString() ?: "n/a",
                        quotas[it to COMMUNITY_NODE_PROVIDER]?.toString() ?: "n/a")
            }
        }.render(StringBuilder()).also { println(it) }
    }
}
