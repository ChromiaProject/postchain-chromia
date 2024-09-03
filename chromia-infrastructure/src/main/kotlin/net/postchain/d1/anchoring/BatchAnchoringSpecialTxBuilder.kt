package net.postchain.d1.anchoring

import net.postchain.d1.anchoring.AnchoringSpecialTxExtension.Companion.OP_BATCH_BLOCK_HEADER
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.OpData

class BatchAnchoringSpecialTxBuilder : AnchoringSpecialTxBuilder {
    companion object {
        const val BATCH_GTX_OP_OVERHEAD = 28
    }

    private val blocksToBeAnchored = mutableListOf<GtvArray>()

    override fun getTxOverheadSize() = OP_BATCH_BLOCK_HEADER.length + BATCH_GTX_OP_OVERHEAD

    override fun calculateRequiredSize(packet: AnchoringPacket): Int = packet.blockRid.size +
            packet.rawHeader.size +
            packet.rawWitness.size

    override fun addAnchorPacket(packet: AnchoringPacket) {
        val gtvHeader: Gtv = GtvDecoder.decodeGtv(packet.rawHeader)
        val gtvWitness = GtvByteArray(packet.rawWitness)

        blocksToBeAnchored.add(gtv(gtv(packet.blockRid), gtvHeader, gtvWitness))
    }

    override fun build() = if (blocksToBeAnchored.isNotEmpty())
        listOf(OpData(OP_BATCH_BLOCK_HEADER, arrayOf(gtv(blocksToBeAnchored)))) else emptyList()
}
