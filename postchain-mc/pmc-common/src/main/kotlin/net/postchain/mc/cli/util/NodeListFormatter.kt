package net.postchain.mc.cli.util

import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import java.time.Instant
import java.util.*

object NodeListFormatter {

    fun render(nodes: List<Array<out Gtv>>, includeInactive: Boolean): StringBuilder {
        return table {
            header("pubkey", "host", "port", "active", "last updated")
            nodes.forEach {
                if (includeInactive || it[3].asBoolean()) {
                    row(
                            it[0].asByteArray().toHex(),
                            it[1].asString(),
                            it[2].asInteger().toString(),
                            it[3].asBoolean().toString(),
                            Date.from(Instant.ofEpochMilli(it[4].asInteger()))
                    )
                }
            }
            hints { borderStyle = Table.BorderStyle.SINGLE_LINE }
        }.render()
    }
}