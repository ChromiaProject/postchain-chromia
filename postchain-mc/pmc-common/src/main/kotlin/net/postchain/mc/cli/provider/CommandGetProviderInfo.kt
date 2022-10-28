package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.defaultLazy
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
        println("""
            Provider: ${providerData.name}
            Pubkey: ${providerData.pubkey.hex()}
            System: ${providerData.system}
            Tier: ${providerData.tier}
            Active: ${providerData.active}
            Action points: $actionPoints
            Belongs to cluster(s): ${providerClusters.joinToString("\n")}
            Nodes:
            ${nodesByProvider.joinToString("\n") { it.pubkey.hex() }}
        """.trimIndent())
    }
}
