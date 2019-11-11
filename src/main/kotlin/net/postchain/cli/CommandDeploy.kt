package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.hexStringToByteArray

@Parameters(commandDescription = "add blockchain configuration")
class CommandDeploy: CommandBase() {

    @Parameter(
            names = ["-brid", "--blockchain-rid"],
            description = "Blockchain ID",
            required = true)
    private var blockchainRID: String = ""

    @Parameter(
            names = ["-bc", "--blockchain-config"],
            description = "Configuration file of blockchain (gtxml or binary)",
            required = true)
    private var blockchainConfigFile = ""

    @Parameter(
            names = ["-h", "--height"],
            description = "Height of configuration",
            required = true)
    private var height = 0L

    @Parameter(
            names = ["-sk", "--private-key"],
            description = "Admin private key",
            required = true)
    private var privKey = ""

    @Parameter(
            names = ["-pk", "--public-key"],
            description = "Admin public key",
            required = true)
    private var pubKey = ""

    override fun key(): String = "deploy"

    override fun execute(): CliResult {
        return try {
            var signer = Pair(pubKey.hexStringToByteArray(), privKey.hexStringToByteArray())
            val cliExecution = CliExecution()
            cliExecution.addBlockchainConfigurtion(config, blockchainRID, height, blockchainConfigFile, signer)
            Ok("Configuration has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}