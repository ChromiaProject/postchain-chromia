package net.postchain.mc

import net.postchain.mc.cli.Cli
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.base.Ok
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    dumpPid()
    when(val cliResult = Cli().parse(args)){
        is CliError -> {
            when(cliResult) {
                is CliError.MissingCommand -> {
                    println(cliResult.message + "\n")
                    Cli().usageCommands()
                    println("\n")
                }
                is CliError.ArgumentNotFound -> {
                    println(cliResult.message + "\n")
                    Cli().usage(cliResult.command)
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

fun dumpPid() {
    val processName = ManagementFactory.getRuntimeMXBean().name
    val pid = processName.split("@")[0]
    File("postchain.pid").writeText(pid)
}
