package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption

class CommandGetNodeInfo : CliktCommand(
    name = "info",
    help = "Get node info for given node pubkey"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val node = CliExecution(config).getNodeInfo(key).asDict()
        println("Active: ${node["active"]!!.asBoolean()}")
        println("Host: ${node["host"]!!.asString()}")
        println("Port: ${node["port"]!!.asInteger()}")
        println("Clusters: ${node["cluster"]?.asArray()?.map { it.asString() }}")
    }
}
