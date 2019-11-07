package net.postchain.cli

interface Command {
    fun key(): String
    fun execute(): CliResult
}