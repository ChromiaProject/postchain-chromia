package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getBlockchainInfoList
import net.postchain.chain0.common.queries.getContainers
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption
import java.lang.StringBuilder

class CommandListContainers : CliktCommand(
        name = "list",
        help = "List all existing containers"
) {
    private val config by configOption()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        val containers = client.getContainers()
        val bcs = client.getBlockchainInfoList(false).groupBy { it.container }
        println("Containers:")
        table {
            header("Name", "Cluster", "Deployer voter set", "Blockchains")
            containers.forEach {
                row(it.name, it.cluster, it.deployer, bcs[it.name]?.joinToString(", ") { bc -> bc.name } ?: "")
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }
            .render(StringBuilder())
            .also { println(it) }
    }
}