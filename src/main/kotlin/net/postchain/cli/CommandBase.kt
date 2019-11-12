package net.postchain.cli

import com.beust.jcommander.Parameter
import net.postchain.common.hexStringToByteArray

abstract class CommandBase : Command {

    @Parameter(
            names = ["-cfg", "--config"],
            description = "cli program config property file path",
            required = true)
    protected var config = ""

    @Parameter(
            names = ["-sk", "--private-key"],
            description = "Admin private key",
            required = true)
    protected var privKey = ""

    @Parameter(
            names = ["-pk", "--public-key"],
            description = "Admin public key",
            required = true)
    protected var pubKey = ""

    protected fun getSigner() = Pair(pubKey.hexStringToByteArray(), privKey.hexStringToByteArray())
}