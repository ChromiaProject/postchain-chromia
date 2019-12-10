package net.postchain.mc.cli.base

import net.postchain.mc.cli.base.CliResult

interface Command {
    fun key(): String
    fun execute(): CliResult
}