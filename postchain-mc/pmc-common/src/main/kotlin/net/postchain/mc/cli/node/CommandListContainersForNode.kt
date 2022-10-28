package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodeContainers
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.util.clientOption

class CommandListContainersForNode : CliktCommand(
        name = "containers",
        help = "List containers for node"
) {
    private val client by clientOption()
    private val key by requiredPubkeyOption()

    override fun run() {
        table {
            client.getNodeContainers(PubKey(key)).forEach {
                row(it.name, it.cluster, it.deployer)
            }
        }.render().also { println(it) }
    }
}