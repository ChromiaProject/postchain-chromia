package net.postchain.mc

import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.CliResult
import net.postchain.mc.cli.base.Ok
import kotlin.system.exitProcess

interface Cli {
    fun parse(args: Array<String>): CliResult
    fun parse(input: String)
    fun usage()
    fun usage(command: String)
    fun usageCommands()
}

fun exec(cli: Cli, args: Array<String>) {
    when(val cliResult = cli.parse(args)){
        is CliError -> {
            when(cliResult) {
                is CliError.MissingCommand -> {
                    println(cliResult.message + "\n")
                    cli.usageCommands()
                    println("\n")
                }
                is CliError.ArgumentNotFound -> {
                    println(cliResult.message + "\n")
                    cli.usage(cliResult.command)
                }
                else -> cliResult.message?.let {
                    println("\n$it\n")
                }
            }
            exitProcess(cliResult.code)
        }
        is Ok -> {
            cliResult.info?.also {
                println("\n$it\n")
            }
            if(!cliResult.isLongRunning){
                exitProcess(cliResult.code)
            }
        }
    }
}