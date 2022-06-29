package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import net.postchain.cli.util.nodeConfigOption
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.BaseClientConfig

class CommandTransferActionPoints : CliktCommand(
    name = "transfer-action-points",
    help = "transfer some of your action points to another provider"
) {
    private val nodeConfig by nodeConfigOption()

    private val pubkey by requiredPubkeyOption()

    private val amount by option("-a", "--amount", help = "number of points to transfer").long().required()

    override fun run() {
        CliExecution(BaseClientConfig.fromPropertiesFile(nodeConfig)).transferActionPoints(pubkey, amount)
        println("Action points have been transferred successfully")
    }
}