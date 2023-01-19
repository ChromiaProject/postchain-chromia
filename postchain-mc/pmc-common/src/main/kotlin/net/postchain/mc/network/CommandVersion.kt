package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.mc.cli.util.clientOption

class CommandVersion : CliktCommand(
        name = "version",
        help = "Shows network version"
) {
    private val client by clientOption()

    override fun run() {
        val version = Version(client).version
        println("Directory1 version: ${version.codename} v${version.semver}")
    }
}