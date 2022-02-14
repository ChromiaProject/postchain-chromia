package net.postchain.mc.cli.base

import com.beust.jcommander.JCommander
import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.ParameterException
import net.postchain.mc.Cli
import org.spongycastle.asn1.x500.style.RFC4519Style.name
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
        parse(input.split(Regex("\\s+")).toTypedArray())
    }

    override fun usage() {
        jCommander.usage()
    }

    override fun usage(command: String) {
        jCommander.commands[command]?.usage()
    }

    override fun usageCommands() {
        println("Commands:")
        jCommander.commands
                .toSortedMap()
                .forEach { (_, cmd) ->
                    cmd.usage()
                }
    }
}