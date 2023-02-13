package net.postchain.mc.cli.container

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.proposal.getContainerLimitsProposal
import net.postchain.chain0.common.queries.getContainerData
import net.postchain.chain0.model.ContainerResourceLimitType
import net.postchain.chain0.nm_api.nmGetBlockchainsForContainer
import net.postchain.chain0.nm_api.nmGetContainerLimits
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.cli.util.nameOption
import net.postchain.mc.cli.util.validateAlphaNumeric

class CommandGetContainerInfo : CliktCommand(
        name = "info",
        help = "Get information about a container"
) {

    private val client by clientOption()

    private val name by nameOption("Container Name").required().validate(validateAlphaNumeric())

    override fun run() {
        val info = client.getContainerData(name)

        table {
            row("Name:", info.name)
            row("Cluster:", info.cluster)
            row("Deployer:", info.deployer)
            row(
                    "Proposed by:",
                    listOf(info.proposedByPubkey.toHex(), info.proposedByName)
                            .filter { it.isNotEmpty() }
                            .joinToString(" / ")
            )
            row("System:", info.system.toString())
            hints {
                defaultAlignment = Table.Hints.Alignment.LEFT
            }
        }.render().also { echo(it) }
    }

    private fun Table.defaultHints() {
        hints {
            borderStyle = Table.BorderStyle.SINGLE_LINE
            defaultAlignment = Table.Hints.Alignment.LEFT
        }
    }
}
