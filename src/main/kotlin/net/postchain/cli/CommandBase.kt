package net.postchain.cli

import com.beust.jcommander.Parameter

abstract class CommandBase : Command {

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true
    )
    protected var config = ""
}