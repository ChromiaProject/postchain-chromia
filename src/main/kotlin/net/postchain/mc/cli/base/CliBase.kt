package net.postchain.mc.cli.base

import com.beust.jcommander.JCommander
import net.postchain.mc.Cli

abstract class CliBase: Cli {
    protected lateinit var jCommander: JCommander
}