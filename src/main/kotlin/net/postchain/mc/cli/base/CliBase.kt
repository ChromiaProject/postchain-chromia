package net.postchain.mc.cli.base

import com.beust.jcommander.JCommander

abstract class CliBase {
    protected lateinit var jCommander: JCommander
}