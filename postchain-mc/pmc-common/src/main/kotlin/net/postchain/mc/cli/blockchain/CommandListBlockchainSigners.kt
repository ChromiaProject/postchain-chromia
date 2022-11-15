package net.postchain.mc.cli.blockchain

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.cli.util.blockchainRidOption
import net.postchain.common.toHex
import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.cli.includeInactiveOption
import net.postchain.mc.cli.util.configOption
import java.time.Instant
import java.util.*

class CommandListBlockchainSigners : CliktCommand(
    name = "signers",
    help = "List blockchain signers"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val listSigners = CliExecution(config).listBlockchainSigners(blockchainRID.toHex())

        table {
            header("pubkey", "host", "port", "active", "Last update")
            listSigners.forEach {
                if (includeInactive || it[3].asBoolean()) {
                    row(it[0].asByteArray().toHex(), it[1].asString(), it[2].asInteger().toString(), it[3].asBoolean().toString(), Date.from(Instant.ofEpochMilli(it[3].asInteger())))
                }
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { println(it) }
    }
}