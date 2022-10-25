package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.chain0.common.queries.getProviderClusters
import net.postchain.chain0.common.queries.getProviderData
import net.postchain.chain0.common.queries.getProviderPoints
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandGetProviderInfo : CliktCommand(
        name = "info",
        help = "Show provider information"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val pubkey = PubKey(key)
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
