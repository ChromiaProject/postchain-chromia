package net.postchain.mc.cli.votingupdates

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class VoterSetMemberCommand : NoOpCliktCommand(
    name = "member",
    help = "Member commands"
)

fun memberCommands() = VoterSetMemberCommand().subcommands(
    CommandListVoterSetMembers(),
)
