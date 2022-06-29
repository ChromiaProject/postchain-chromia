package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandGetNodeInfo : CliktCommand(
    name = "info",
    help = "Get node info for given node pubkey"
) {
    private val nodeConfig by nodeConfigOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val node = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).getNodeInfo(key).asDict()
        println("Active: ${node["active"]!!.asBoolean()}")
        println("Host: ${node["host"]!!.asString()}")
        println("Port: ${node["port"]!!.asInteger()}")
        println("Clusters: ${node["cluster"]?.asArray()?.map { it.asString() }}")
    }
}
