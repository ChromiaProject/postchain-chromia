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
import java.util.Date

class CommandListBlockchainReplicas : CliktCommand(
        name = "replicas",
        help = "List blockchain replicas"
) {
    private val config by configOption()

    private val blockchainRID by blockchainRidOption()

    private val includeInactive by includeInactiveOption()

    override fun run() {
        val list = CliExecution(config).listBlockchainReplicas(blockchainRID.toHex())
        table {
            header("Blockchain RID", "pubkey", "host", "port", "active", "Last update")
            list.forEach {
                if (includeInactive || it[4].asBoolean()) {
                    row(it[0].asByteArray().toHex(), it[1].asByteArray().toHex(), it[2].asString(), it[3].asInteger().toString(), it[4].asBoolean().toString(), Date.from(Instant.ofEpochMilli(it[5].asInteger())))
                }
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render().also { println(it) }
    }
}