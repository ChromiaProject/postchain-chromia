package net.postchain.d1.anchoring

import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnchoringSpecialTxBuilderTest {

    @Test
    fun multiOpSize() {
        val packet = AnchoringPacket(
                height = 0,
                blockRid = ByteArray(32) { 17 },
                rawHeader = GtvEncoder.encodeGtv(gtv(gtv("foobar"), gtv(4711))),
                rawWitness = ByteArray(16) { 123 }
        )
        MultiOpAnchoringSpecialTxBuilder().apply {
            val size = getTxOverheadSize() + calculateRequiredSize(packet)
            addAnchorPacket(packet)
            val ops = build()
            assertEquals(GtvEncoder.encodeGtv(GtxOp.fromOpData(ops[0]).toGtv()).size, size)
        }
    }

    @Test
    fun batchOpSize() {
        val packet = AnchoringPacket(
                height = 0,
                blockRid = ByteArray(32) { 17 },
                rawHeader = GtvEncoder.encodeGtv(gtv(gtv("foobar"), gtv(4711))),
                rawWitness = ByteArray(16) { 123 }
        )
        BatchAnchoringSpecialTxBuilder().apply {
            val size = getTxOverheadSize() + calculateRequiredSize(packet)
            addAnchorPacket(packet)
            val ops = build()
            assertEquals(GtvEncoder.encodeGtv(GtxOp.fromOpData(ops[0]).toGtv()).size, size)
        }
    }
}
