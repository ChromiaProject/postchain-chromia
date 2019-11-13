package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "remove peer")
class CommandRemovePeer: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "Peer's public key",
            required = true)
    private var key = ""

    override fun key(): String = "remove-peer"

    override fun execute(): CliResult {
        return try {
            CliExecution().removePeer(config, key, getSigner())
            Ok("Peer has been removed successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}