package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.crypto.PubKey
import net.postchain.mc.cli.base.pubkey
import net.postchain.mc.cli.util.clientOption
import java.time.Instant
import java.util.*

class CommandListProviderNodes : CliktCommand(
        name = "nodes",
        help = "List nodes by provider"
) {
    private val client by clientOption()

    private val key by option("-pk", "--pubkey").convert { PubKey(it) }.defaultLazy { client.config.pubkey() }

    override fun run() {
        val nodes = client.getNodesByProvider(key)
        if (nodes.isEmpty()) {
            echo("No nodes for provider $key")
        } else {
            echo("Nodes for provider $key")
            table {
                hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
                header("Pubkey", "Host", "Port", "Api port", "Active", "Last updated")
                nodes.forEach {
                    row(
                            it.pubkey.toHex(),
                            it.host,
                            it.port.toString(),
                            it.apiUrl,
                            it.active.toString(),
                            Date.from(Instant.ofEpochMilli(it.lastUpdated)).toString()
                    )
                }
            }.render().also { echo(it) }
        }
    }
}
