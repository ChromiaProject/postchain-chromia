package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import net.postchain.chain0.common.queries.getBlockchainLastHeight
import net.postchain.cli.util.*
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.directory1.CliExecutionD1
import net.postchain.mc.cli.util.configOption
import org.spongycastle.crypto.tls.ConnectionEnd.client

class CommandProposeConfiguration : CliktCommand(
    name = "update",
    help = """
        Propose new configuration to blockchain at specific height. 
        Height must be > current height and > all previously approved configuration heights.
        Use force flag -f to override previously added configs or to squeeze in a configuration 
        at a height < previously approved config heights. Change will be applied after voting.
        """.trimIndent()
) {
    private val config by configOption()

    private val blockchainConfigFile by blockchainConfigOption()

    private val blockchainRID by blockchainRidOption()

    private val height by heightOption()

    private val force by forceOption()

    override fun run() {
        val addToHeight = height ?: (ClientUtil.fromConfig(config).getBlockchainLastHeight(blockchainRID.data) + 5)
        CliExecutionD1(config)
            .proposeConfiguration(blockchainRID.toHex(), blockchainConfigFile, addToHeight, null, force)
        println("Configuration update has been proposed")
    }
}