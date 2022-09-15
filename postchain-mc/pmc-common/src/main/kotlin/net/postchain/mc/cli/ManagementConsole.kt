package net.postchain.mc.cli

import com.github.ajalt.clikt.core.NoOpCliktCommand

class ManagementConsole : NoOpCliktCommand(name = "postchain-mc") {
    override fun aliases(): Map<String, List<String>> {
        return mapOf(
            "init" to listOf("network", "initialize"),
            "initialize" to listOf("network", "initialize"),
            "blockchains" to listOf("blockchain", "list"),
            "bcs" to listOf("blockchain", "list"),
            "votersets" to listOf("voterset", "list"),
            "containers" to listOf("container", "list"),
            "clusters" to listOf("cluster", "list"),
        )
    }
}
