package net.postchain.mc.cli.base

import com.beust.jcommander.JCommander
import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.ParameterException
import net.postchain.mc.Cli
import java.sql.SQLException

abstract class CliBase: Cli {
    protected lateinit var jCommander: JCommander

    protected abstract val commands: Map<String, Command>

    override fun parse(args: Array<String>): CliResult {
        return try {
            jCommander.parse(*args)
            if (jCommander.parsedCommand == null) {
                CliError.MissingCommand(message = "Expected a command, got <no-command>")
            } else {
                commands[jCommander.parsedCommand]?.execute()
                        ?: CliError.ArgumentNotFound(command = jCommander.parsedCommand)
            }
        } catch (e: MissingCommandException) {
            CliError.MissingCommand(e.unknownCommand)
        } catch (e: ParameterException) {
            CliError.ArgumentNotFound(command = jCommander.parsedCommand)
        } catch (e: SQLException) {
            CliError.DatabaseError(e)
        }
    }

    override fun parse(input: String) {
        jCommander.parse(*input.split(Regex("\\s+")).toTypedArray())
        commands[jCommander.parsedCommand]?.execute()
    }

    override fun usage() {
        jCommander.usage()
    }

    override fun usage(command: String) {
        jCommander.usage(command)
    }

    override fun usageCommands() {
        val usage = jCommander.commands.keys
                .asSequence()
                .sorted()
                .map { cmd ->
                    "${cmd.padEnd(35, ' ')}${jCommander.getCommandDescription(cmd)}"
                }.joinToString(
                        separator = "\n  ",
                        prefix = "Commands:\n  "
                )

        println(usage)
    }
}