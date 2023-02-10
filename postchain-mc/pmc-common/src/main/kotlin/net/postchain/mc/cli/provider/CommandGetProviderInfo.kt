package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.defaultLazy
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.*
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.pubkeyOption

class CommandGetProviderInfo : CliktCommand(
        name = "info",
        help = "Show provider information"
) {
    private val config by configOption()

    private val pubkey by pubkeyOption().defaultLazy { config.pubkey() }

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val providerData = client.getProviderData(pubkey)
        val actionPoints = client.getProviderPoints(pubkey)
        val providerClusters = client.getProviderClusters(pubkey)
        val nodesByProvider = client.getNodesByProvider(pubkey)
        table {
            row("Provider:", providerData.name)
            row("Pubkey:", providerData.pubkey.toHex())
            row("System:", providerData.system.toString())
            row("Tier:", providerData.tier.toString())
            row("Active:", providerData.active.toString())
            row("Action points:", actionPoints.toString())
            row("Belongs to cluster(s)", providerClusters.joinToString(","))
            nodesByProvider.forEachIndexed { index, node ->
                row("Node $index", node.pubkey.toHex())
            }
            hints {
                defaultAlignment = Table.Hints.Alignment.LEFT
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }.render().also { echo(it) }
    }
}
