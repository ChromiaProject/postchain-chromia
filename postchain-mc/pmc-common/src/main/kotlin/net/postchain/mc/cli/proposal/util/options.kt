package net.postchain.mc.cli.proposal.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long

fun CliktCommand.proposalIndexOption() = option("--id", help = "Id of the proposal").long()