package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.Chromia0CliExecution

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
            Chromia0CliExecution(loadAppConfig()).addNode(key, host, port)
            Ok("Node has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}