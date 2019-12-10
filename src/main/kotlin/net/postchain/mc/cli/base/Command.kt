package net.postchain.mc.cli.base

interface Command {
    fun key(): String
    fun execute(): CliResult
}