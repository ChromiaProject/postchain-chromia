package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "Get node info for given node pubkey")
class CommandGetNodeInfo : CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "node's public key",
            required = true)
    private var key = ""


    override fun key(): String = "node-info"

    override fun execute(): CliResult {
        return try {
            val node = CliExecution(loadAppConfig()).getNodeInfo(key).asDict()
            println("Active: ${node["active"]!!.asBoolean()}")
            println("Host: ${node["host"]!!.asString()}")
            println("Port: ${node["port"]!!.asInteger()}")
            println("Clusters: ${node["cluster"]?.asArray()?.map { it.asString() }}")
            Ok("Get node info successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}
