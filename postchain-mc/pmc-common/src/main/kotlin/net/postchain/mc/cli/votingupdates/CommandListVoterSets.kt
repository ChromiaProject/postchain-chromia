package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.chain0.common.voting.getVoterSets
import net.postchain.mc.cli.util.clientOption

class CommandListVoterSets : CliktCommand(
        name = "list",
        help = "List all voter sets"
) {
    private val client by clientOption()

    override fun run() {
        val voterSets = client.getVoterSets()
        if (voterSets.isEmpty()) {
            echo("No voter sets")
        } else {
            echo("Voter sets:")
            table {
                header("Name", "Governor", "Majority level")
                voterSets.forEach {
                    row(it.name, it.gorvernor, formatThreshold(it.threshold))
                }
                hints {
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                }
            }.render().also { echo(it) }
        }
    }
}