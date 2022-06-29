package net.postchain.mc.cli.container

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.CommandBase
import net.postchain.mc.cli.base.Ok
import net.postchain.mc.cli.common0.CliExecution

@Parameters(commandDescription = "propose a new container in an existing cluster and give authority to deployer voter set to deploy bcs in it.")
class CommandProposeContainer : CommandBase() {

    @Parameter(
            names = ["-n", "--name"],
            description = "Name of new container. Alphanumerics only")
    private var name = ""

    @Parameter(
            names = ["-a", "--auto-generate-name"],
            description = "Set if container name should be aoutogenerated")
    private var autoGenerateContainerName = false


    @Parameter(
            names = ["-c", "--cluster"],
            description = "Name of cluster to put container in. Must exist in database",
            required = true)
    private var clusterName = ""

    @Parameter(
            names = ["-d", "--deployer"],
            description = "Name of voter set authorized to operate in container.",
            required = true)
    private var deployerName = ""

    override fun key(): String = "propose-container"

    override fun execute(): CliResult {

        return try {
            // Input validation of container name
            var resName = ""
            if (name == "") {
                if (autoGenerateContainerName) {
                    resName = autoGenerateName()
                } else {
                    throw CliError.Companion.CliException("When container name is not given, flag -a must be set to " +
                            "autogenerate a container name")
                }
            } else if (isAlphanumeric(name) and (name.length <= NAME_LENGTH_MAX)) {
                resName = name
            } else {
                throw CliError.Companion.CliException("Invalid container name: $name. Only [0-9A-Za-z] is allowed and max length is $NAME_LENGTH_MAX.")
            }
            CliExecution(loadAppConfig()).proposeContainer(resName, clusterName, deployerName)
            Ok("proposal for container with name $name has been added successfully")
        } catch (e: CliError.Companion.CliException) {
            CliError.CommandNotAllowed(message = e.message)
        }
    }
}