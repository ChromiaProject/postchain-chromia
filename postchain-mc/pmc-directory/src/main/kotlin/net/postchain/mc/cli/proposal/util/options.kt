package net.postchain.mc.cli.proposal.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long

fun CliktCommand.proposalIndexOption() = option(
"-idx", "--proposal-index",
help = "Unique index, used as reference to a proposed configuration update, of various type. Could be e.g. provider/node management or rell-module updates"
)
.long()