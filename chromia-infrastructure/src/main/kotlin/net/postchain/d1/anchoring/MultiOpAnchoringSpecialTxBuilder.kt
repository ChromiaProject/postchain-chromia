package net.postchain.d1.anchoring

import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BLOCK_HEADER
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.OpData

class MultiOpAnchoringSpecialTxBuilder : AnchoringSpecialTxBuilder {

    companion object {
        const val GTX_OP_OVERHEAD = 20
    }

    private val operations = mutableListOf<OpData>()

    override fun getTxOverheadSize() = 0

    override fun calculateRequiredSize(packet: AnchoringPacket): Int = OP_BLOCK_HEADER.length +
            packet.blockRid.size +
            packet.rawHeader.size +
            packet.rawWitness.size +
            GTX_OP_OVERHEAD

    override fun addAnchorPacket(packet: AnchoringPacket) {
        val gtvHeader: Gtv = GtvDecoder.decodeGtv(packet.rawHeader)
        val gtvWitness = GtvByteArray(packet.rawWitness)

        operations.add(OpData(OP_BLOCK_HEADER, arrayOf(gtv(packet.blockRid), gtvHeader, gtvWitness)))
    }

    override fun build() = operations
}
