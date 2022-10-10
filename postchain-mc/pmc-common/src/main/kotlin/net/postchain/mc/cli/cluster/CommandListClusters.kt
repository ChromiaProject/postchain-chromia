package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.queries.getClusters
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption
import java.lang.StringBuilder

class CommandListClusters : CliktCommand(
    name = "list",
    help = "List all existing clusters"
) {
    private val config by lazy { read() }
    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
        val client = ClientUtil.fromConfig(config)
        println("Clusters:")
        table {
            header("Name", "Governor", "Container deployer", "Operational")
            client.getClusters().forEach {
                row(it.name, it.governor, it.containerDeployer, it.operational.toString())
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
            }
        }
            .render(StringBuilder())
            .also { println(it) }
    }
}