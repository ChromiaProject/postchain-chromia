package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class VoterSetCommand : NoOpCliktCommand(
    name = "voterset",
    help = "Voter set commands"
)

fun voterSetCommands() = VoterSetCommand().subcommands(
    CommandProposeVoterSetUpdate(),
    CommandCreateVoterSet(),
    CommandListVoterSets(),
    CommandVoterSetInfo(),
)
