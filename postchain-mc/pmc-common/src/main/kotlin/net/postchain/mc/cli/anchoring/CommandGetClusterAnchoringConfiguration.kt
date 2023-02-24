package net.postchain.mc.cli.anchoring

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.cluster_anchoring.getClusterAnchoringConfiguration
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption

class CommandGetClusterAnchoringConfiguration : CliktCommand(
        name = "get",
        help = "Get cluster anchoring configuration"
) {
    private val config by configOption()

    private val save by option(help = "where to save configuration XML").file(canBeFile = true, canBeDir = false)

    override fun run() {
        val ac = ClientUtil.fromConfig(config).getClusterAnchoringConfiguration()
        val xmlGtv = GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(ac))
        if (save != null) {
            save!!.parentFile.mkdirs()
            save!!.writeText(xmlGtv)
        } else {
            println(xmlGtv)
        }
    }
}
