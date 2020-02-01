package net.postchain.mc.cli.node

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecution

@Parameters(commandDescription = "list nodes")
class CommandListNodes : CommandBase() {

    @Parameter(
            names = ["-p", "--provider"],
            description = "Provider information",
            required = false)
    private var showProvider = false

    override fun key(): String = "list-nodes"

    override fun execute(): CliResult {
        return try {
            val cliExecution = CliExecution(loadAppConfig())
            val nodes : List<Gtv>
            if (showProvider) {
                nodes = cliExecution.listNodesWithProvider()
                nodes.forEach {
                    val n = it.asDict()
                    println("host: ${n["host"]?.asString()}")
                    println("port: ${n["port"]?.asInteger()}")
                    println("pubkey: ${n["pubkey"]?.asByteArray()?.toHex()}")
                    println("last_update: ${n["last_updated"]?.asInteger()}")
                    println("provider pubkey: ${n["provider"]?.asByteArray()?.toHex()}")
                    println("provider name: ${n["name"]?.asString()}")
                    println("provider active: ${n["provider_active"]?.asBoolean()}")
                    println("provider beneficiary: ${n["beneficiary"]?.asByteArray()?.toHex()}")
                }
            } else {
                nodes = cliExecution.listNodes()
                nodes.forEach { info ->
                    println("host: ${info.get(0).asString()}")
                    println("port: ${info.get(1).asInteger()}")
                    println("pubkey: ${info.get(2).asByteArray().toHex()}")
                    println("last_update: ${info.get(3).asInteger()}")
                }
            }
            Ok("List nodes successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}