package net.postchain.mc.cli

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.table
import net.postchain.PostchainNode
import net.postchain.chain0.common.directoryVersion
import net.postchain.mc.cli.util.clientOption

class CommandVersion : CliktCommand(
        name = "version",
        help = "Shows components versions"
) {
    private val client by clientOption()

    override fun run() {
        table {
            row("PMC version", this::class.java.`package`.implementationVersion ?: "(unknown)")
            row("Postchain version", PostchainNode::class.java.`package`.implementationVersion ?: "(unknown)")
            row("Directory1 version", client.directoryVersion())
        }.render().also { println(it) }
    }
}