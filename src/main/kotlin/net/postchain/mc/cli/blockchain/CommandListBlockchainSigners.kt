package net.postchain.mc.cli.blockchain

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "List blockchain signers")
class CommandListBlockchainSigners : CommandBase() {


    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain Rid",
            required = true)
    private var blockchainRID = ""

    override fun key(): String = "list-blockchain-signers"

    override fun execute(): CliResult {
        return try {
            val listBlockchains = CliExecution(loadAppConfig()).listBlockchainSigners(blockchainRID)
            listBlockchains.forEach { item ->
                println("BlockchainRID: ${item.get(0).asByteArray().toHex()}")
                println("Node pubkey: ${item.get(1).asByteArray().toHex()}")
                println("Node host: ${item.get(2).asString()}")
                println("Node port: ${item.get(3).asInteger()}")
                println("Node active: ${item.get(4).asBoolean()}")
                println("Node last_update: ${item.get(5).asInteger()}")
            }
            Ok("List blockchain signers successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}