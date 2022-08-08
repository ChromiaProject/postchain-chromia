package net.postchain.mc.cli.provider

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.cli.util.requiredPubkeyOption
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.util.ProvidersPrinter
import net.postchain.mc.cli.util.configOption

class CommandGetProviderInfo : CliktCommand(
        name = "info",
        help = "Show provider information"
) {
    private val config by configOption()

    private val key by requiredPubkeyOption()

    override fun run() {
        val cliExecution = CliExecution(config)
        val provider = cliExecution.getProviderInfo(key)
        val providerList = arrayListOf(provider)
        ProvidersPrinter.printProviders(providerList)

        val points = cliExecution.listProvidersActionPoints(key)
        println("action points: $points")
        println("")

        val clusters = cliExecution.listClustersForProvider(key)
        println("belongs to cluster(s): $clusters")
        println("")
    }
}
