package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import de.m3y.kformat.table
import net.postchain.chain0.common.voting.getVoterSetInfo
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandVoterSetInfo : CliktCommand(
    name = "info",
    help = "Show information of voter set"
) {
    private val config by configOption()

    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val voterSet = ClientUtil.fromConfig(config).getVoterSetInfo(name)
        table {
            row("Voter set", voterSet.name)
            row("Governed by", voterSet.governor)
            row("Threshold", formatThreshold(voterSet.threshold))
            voterSet.members.forEachIndexed { index, bytes ->  row("Member $index", bytes.toHex()) }
        }
            .render()
            .also { println(it) }
    }
}
