package net.postchain.mc.network

import com.github.ajalt.clikt.core.CliktCommand
import net.postchain.chain0.common.directoryVersion
import net.postchain.mc.cli.util.clientOption

class CommandVersion : CliktCommand(
        name = "version",
        help = "Shows network version"
) {
    private val client by clientOption()

    override fun run() {
        try {
            println("Directory1 version: ${client.directoryVersion()}")
        } catch (e: Throwable) {
            println("Can't get network version:" + e.message)
        }
    }
}