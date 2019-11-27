package net.postchain.mc.cli

import com.beust.jcommander.Parameter
import net.postchain.common.hexStringToByteArray

abstract class CommandBase : Command {

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""
}