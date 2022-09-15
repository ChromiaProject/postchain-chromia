package net.postchain.mc.cli.util

import net.postchain.common.toHex
import net.postchain.gtv.Gtv

object NodesPrinter {

    fun printNodes(nodes: List<Gtv>): String {
        val headerTemplate = "%-70s%-30s%-5s"
        val template = "%-70s%-30s%-5b"
        val header = headerTemplate.format("NODE PUBKEY", "HOST:PORT / API PORT", "ACTIVE")

        return nodes.joinToString("\n", prefix = "$header\n") {
            val n = it.asDict()
            val pubkey = n["pubkey"]?.asByteArray()?.toHex()
            val host = n["host"]?.asString()
            val port = n["port"]?.asInteger()
            val apiPort = n["api_port"]?.asInteger()
            val active = n["active"]?.asBoolean()
            template.format(pubkey, "$host:$port / $apiPort", active)
        }
    }

}