package net.postchain.mc.cli.provider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.chromia0.CliExecutionC0

@Parameters(commandDescription = "update provider data (name, beneficiary,...")
class CommandUpdateProvider: CommandBase() {

    @Parameter(
            names = ["-k", "--key"],
            description = "provider's public key",
            required = true)
    private var key = ""

    @Parameter(
            names = ["-n", "--name"],
            description = "provider's name",
            required = false)
    private var name = ""

    @Parameter(
            names = ["-b", "--beneficiary"],
            description = "provider's beneficiary account id",
            required = false)
    private var beneficiary = ""

    override fun key() = "update-provider"

    override fun execute(): CliResult {
        return try {
            CliExecutionC0(loadAppConfig()).updateProvider(key, name, beneficiary)
            Ok("Provider has been updated successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}