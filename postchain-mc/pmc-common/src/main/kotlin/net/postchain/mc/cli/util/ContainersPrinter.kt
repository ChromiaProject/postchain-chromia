package net.postchain.mc.cli.util

import net.postchain.gtv.Gtv

object ContainersPrinter {

    fun print(containers: List<Gtv>, printCluster: Boolean): String {
        return if (printCluster) {
            val template = "%-15s%-15s%-15s"
            val header = template.format("CONTAINER", "CLUSTER", "DEPLOYER")

            containers.joinToString("\n", prefix = "$header\n") {
                val c = it.asDict()
                template.format(name(c), cluster(c), deployer(c))
            }
        } else {
            val template = "%-15s%-15s"
            val header = template.format("CONTAINER", "DEPLOYER")

            containers.joinToString("\n", prefix = "$header\n") {
                val c = it.asDict()
                template.format(name(c), deployer(c))
            }
        }
    }

    private fun name(gtv: Map<String, Gtv>) = gtv["name"]?.asString()
    private fun cluster(gtv: Map<String, Gtv>) = gtv["cluster"]?.asString()
    private fun deployer(gtv: Map<String, Gtv>) = gtv["deployer"]?.asString()

}