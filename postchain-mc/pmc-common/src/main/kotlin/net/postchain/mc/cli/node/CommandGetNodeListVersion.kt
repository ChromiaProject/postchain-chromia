package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.nm_api.nmGetPeerListVersion
import net.postchain.mc.cli.util.nopClientOption

class CommandGetNodeListVersion : CliktCommand(
        name = "version",
        help = "Peer list version"
) {
    private val client by nopClientOption()

    override fun run() {
        val version = client.nmGetPeerListVersion()
        println("version: $version")
    }
}