package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import net.postchain.cli.util.blockchainRidOption
import net.postchain.cli.util.heightOption
import net.postchain.cli.util.nodeConfigOption
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandGetBlockchainConfiguration : CliktCommand(
    name = "get",
    help = "Get blockchain configuration"
) {
    private val nodeConfig by nodeConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val height by heightOption().default(-1L)

    override fun run() {
        val bc = CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig))
            .getBlockchainConfiguration(blockchainRID.toHex(), height)
        if (height == -1L) {
            println("Blockchain configuration at current:")
        } else {
            println("Blockchain configuration at height: $height")
        }
        println(GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(bc)))
    }
}
