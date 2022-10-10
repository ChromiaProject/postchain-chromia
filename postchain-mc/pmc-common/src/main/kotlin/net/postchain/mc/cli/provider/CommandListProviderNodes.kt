package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getNodesByProvider
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import java.lang.StringBuilder
import java.time.Instant
import java.util.Date

class CommandListProviderNodes : CliktCommand(
    name = "nodes",
    help = "List nodes by provider"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        println("Nodes for provider $key")
        table {
            header("Pubkey", "Host", "Port", "Api port", "Active", "Last updated")

            ClientUtil.fromConfig(config).getNodesByProvider(key.hexStringToByteArray()).forEach {
                row(
                    it.pubkey.toHex(),
                    it.host,
                    it.port,
                    "",
                    it.active.toString(),
                    Date.from(Instant.ofEpochMilli(it.lastUpdated))
                )
            }
        }
            .render(StringBuilder())
            .also { println(it) }
    }
}
