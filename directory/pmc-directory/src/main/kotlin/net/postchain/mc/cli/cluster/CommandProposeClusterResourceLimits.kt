package net.postchain.mc.cli.cluster

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "Propose new resource limits for given cluster. There are three types of limits. " +
        "Proposal can contain one, two, or all three types.")
class CommandProposeClusterResourceLimits: CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of cluster",
            required = true)
    private var containerName = ""

    @Parameter(
            names = ["-r", "--ram"],
            description = "ram limit")
    private var ram: Long? = null

    @Parameter(
            names = ["-c", "--cpu"],
            description = "ram limit")
    private var cpu: Long? = null

    @Parameter(
            names = ["-s", "--storage"],
            description = "ram limit")
    private var storage: Long? = null

    override fun key(): String = "propose-cluster-limits"

    override fun execute(): CliResult {
        val limitMap = mutableMapOf<String, Long>()
        ram?.let { limitMap.put("ram", it) }
        cpu?.let { limitMap.put("cpu", it) }
        storage?.let { limitMap.put("storage", it) }

        return try {
            CliExecution(loadAppConfig()).proposeClusterLimits(containerName, limitMap)
            Ok("proposal has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }

}