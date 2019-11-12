package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add system peer")
class CommandAddSystemPeer: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "System peer's public key",
            required = true)
    private var key = ""

    override fun key(): String = "add-system-peer"

    override fun execute(): CliResult {
        return try {
            CliExecution().addSystemPeer(config, key, getSigner())
            Ok("System Peer has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}