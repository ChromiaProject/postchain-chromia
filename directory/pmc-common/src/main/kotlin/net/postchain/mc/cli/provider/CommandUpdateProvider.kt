package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandUpdateProvider : CliktCommand(
    name = "update",
    help = "update provider data (name, beneficiary,..."
) {
    private val config by configOption()
    private val key by requiredPubkeyOption()

    private val name by nameOption("Provider name")

    private val beneficiary by option("-b", "--beneficiary",
    help = "Providers beneficiary account id")

    override fun run() {
            CliExecution(config).updateProvider(key, name, beneficiary)
            println("Provider has been updated successfully")
    }
}
