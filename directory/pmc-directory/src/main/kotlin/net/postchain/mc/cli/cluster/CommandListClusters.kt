package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.chain0.common.queries.listClusters
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandListClusters : CliktCommand(
    name = "list",
    help = "List all existing clusters"
) {
    private val config by configOption()
    private val includeInactive by option("-i", "--includeinactive", help = "Include disabled/removed clusters (not implemented yet)").flag()

    override fun run() {
        ClientUtil.fromConfig(config).listClusters().forEach { println(it) }
    }
}