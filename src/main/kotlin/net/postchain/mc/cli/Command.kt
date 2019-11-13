package net.postchain.mc.cli

interface Command {
    fun key(): String
    fun execute(): CliResult
}