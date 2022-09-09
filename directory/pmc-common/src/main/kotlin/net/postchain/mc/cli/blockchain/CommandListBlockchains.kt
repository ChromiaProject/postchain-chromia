package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getBlockchainClusters
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import org.http4k.routing.header
import java.awt.SystemColor.info
import java.lang.StringBuilder

class CommandListBlockchains : CliktCommand(
    name = "list",
    help = "List blockchains"
) {
    private val config by configOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val bcs = client.getBlockchains(includeInactive)
        val clusterInfos = client.getBlockchainClusters(includeInactive).associateBy { BlockchainRid(it.brid) }
        table {
            header("Name", "Rid", "Container", "Cluster")

            bcs.forEach {
                val info = clusterInfos[BlockchainRid(it.rid)]
                row(it.name, it.rid.toHex(), info?.container ?: "", info?.cluster ?: "")
            }

            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }
            .render(StringBuilder())
            .also { println(it) }
    }
}