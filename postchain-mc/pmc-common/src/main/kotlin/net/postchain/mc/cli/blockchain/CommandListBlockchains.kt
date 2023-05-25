package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getBlockchainInfoList
import net.postchain.chain0.version.apiVersion
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.compatibility.ApiCompatV3.getBlockchainInfoListV3

class CommandListBlockchains : CliktCommand(
        name = "list",
        help = "List blockchains"
) {
    private val config by configOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val apiVersion = client.apiVersion()
        when {
            apiVersion >= 4 -> {
                val blockchains = client.getBlockchainInfoList(includeInactive)
                if (blockchains.isEmpty()) {
                    echo("No blockchains")
                } else {
                    echo("Blockchains:")
                    table {
                        header("Name", "Rid", "State", "Container", "Cluster")
                        blockchains.forEach {
                            row(it.name, it.rid.toHex(), it.state.toString(), it.container ?: "N/A", it.cluster ?: "N/A")
                        }
                        hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
                    }.render().also { echo(it) }
                }
            }

            else -> {
                val blockchains = client.getBlockchainInfoListV3(includeInactive)
                if (blockchains.isEmpty()) {
                    echo("No blockchains")
                } else {
                    echo("Blockchains:")
                    table {
                        header("Name", "Rid", "Active", "Container", "Cluster")

                        blockchains.forEach {
                            row(it.name, it.rid.toHex(), it.active.toString(), it.container, it.cluster)
                        }

                        hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
                    }.render().also { echo(it) }
                }
            }
        }
    }
}