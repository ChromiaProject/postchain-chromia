package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import net.postchain.chain0.common.voting.getVoterSetInfo
import net.postchain.common.toHex
import net.postchain.mc.cli.base.ClientUtil
import net.postchain.mc.cli.config.PmcConfigProvider.read
import net.postchain.mc.cli.util.configOption
import net.postchain.mc.cli.util.nameOption

class CommandVoterSetInfo : CliktCommand(
    name = "info",
    help = "Show information of voter set"
) {
    private val config by lazy { read() }

    private val name by nameOption("Name of voter set").required()

    override fun run() {
        val voterSet = ClientUtil.fromConfig(config).getVoterSetInfo(name)
        println("""
            Voter set: ${voterSet.name}
            Governed by: ${voterSet.governor}
            Threshold: ${formatThreshold(voterSet.threshold)}
            Members:
            ${voterSet.members.joinToString("\n") { it.toHex() }}
        """.trimIndent()
        )
    }
}
