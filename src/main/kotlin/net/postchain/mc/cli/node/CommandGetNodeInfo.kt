package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "Get node info")
class CommandGetNodeInfo : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "node's public key",
            required = true)
    private var key = ""

    override fun key(): String = "get-node-info"

    override fun execute(): CliResult {
        return try {
            val node = CliExecutionC0(loadAppConfig()).getNodeInfo(key).asDict()
            println("Status: ${node["active"]!!.asBoolean()}")
            println("Host: ${node["host"]!!.asString()}")
            println("Port: ${node["port"]!!.asInteger()}")
            Ok("Get node info successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
