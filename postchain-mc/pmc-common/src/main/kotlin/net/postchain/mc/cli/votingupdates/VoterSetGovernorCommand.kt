package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class VoterSetGovernorCommand : NoOpCliktCommand(
    name = "governor",
    help = "Governor commands"
)

fun governorCommands() = VoterSetGovernorCommand().subcommands(
    CommandGetVoterSetGovernor(),
)
