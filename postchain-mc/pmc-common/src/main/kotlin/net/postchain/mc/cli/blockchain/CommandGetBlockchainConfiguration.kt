package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.mc.cli.blockchainRidOption
import net.postchain.mc.cli.heightOption
import net.postchain.mc.cli.util.clientOption

class CommandGetBlockchainConfiguration : CliktCommand(
        name = "get",
        help = "Get blockchain configuration"
) {
    private val client by clientOption()

    private val blockchainRID by blockchainRidOption()

    private val height by heightOption().default(-1L)

    private val save by option(help = "where to save configuration XML").file(canBeFile = true, canBeDir = false)

    override fun run() {
        val actualHeight = if (height == -1L) {
            val current = client.getBlockchainLastHeight(blockchainRID)
            echo("Blockchain configuration at current height: $current")
            current
        } else {
            echo("Blockchain configuration at height: $height")
            height
        }

        val bcConfig = client.nmGetBlockchainConfiguration(blockchainRID, actualHeight)
        if (bcConfig == null) {
            echo("is absent")
        } else {
            val xmlGtv = GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(bcConfig))
            if (save != null) {
                save!!.parentFile.mkdirs()
                save!!.writeText(xmlGtv)
            } else {
                println(xmlGtv)
            }
        }
    }
}
