package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.nm_api.nmComputeBlockchainList
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.toHex
import net.postchain.mc.cli.util.clientOption

class CommandListBlockchainsForNode : CliktCommand(
        name = "blockchains",
        help = "List blockchains for node"
) {
    private val client by clientOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val listBlockchains = client.nmComputeBlockchainList(key.data)
        listBlockchains.forEach { blockchain ->
            println(blockchain.toHex())
        }
    }
}