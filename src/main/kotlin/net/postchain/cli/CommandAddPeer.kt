package net.postchain.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add peer")
class CommandAddPeer: CommandBase() {

    @Parameter(
            names = ["-h", "--host"],
            description = "Peer's host",
            required = true)
    private var host = ""

    @Parameter(
            names = ["-p", "--port"],
            description = "Peer's port",
            required = true)
    private var port = 0L

    @Parameter(
            names = ["-k", "--key"],
            description = "Peer's public key",
            required = true)
    private var key = ""

    override fun key(): String = "add-peer"

    override fun execute(): CliResult {
        return try {
            CliExecution().addPeer(config, host, port, key, getSigner())
            Ok("Peer has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}