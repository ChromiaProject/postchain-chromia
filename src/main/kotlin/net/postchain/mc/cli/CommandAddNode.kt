package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(commandDescription = "add node")
class CommandAddNode: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "Node's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-h", "--host"],
            description = "Node's host",
            required = true)
    private var host = ""

    @Parameter(
            names = ["-p", "--port"],
            description = "Node's port",
            required = true)
    private var port = 0L

    override fun key(): String = "add-node"

    override fun execute(): CliResult {
        return try {
            CliExecution().addNode(config, key, host, port)
            Ok("Node has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}