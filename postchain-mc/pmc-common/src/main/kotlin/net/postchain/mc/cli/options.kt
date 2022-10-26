package net.postchain.mc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

fun CliktCommand.includeInactiveOption() = option(
        "-i", "--includeinactive",
        help = "Include disabled/removed clusters (not implemented yet)"
).flag()
