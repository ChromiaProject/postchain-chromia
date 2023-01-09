package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusters
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandListClusters : CliktCommand(
        name = "list",
        help = "List all existing clusters"
) {
    private val config by configOption()
    override fun run() {
        val client = ClientUtil.fromConfig(config)
        table {
            header("Name", "Governor", "Operational")
            client.getClusters().forEach {
                row(it.name, it.governor, it.operational.toString())
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }.render().also { println(it) }
    }
}
