package net.postchain.d1.anchoring

import net.postchain.gtx.data.OpData

interface AnchoringSpecialTxBuilder {

    /**
     * Size of any operation overhead that is not per packet but in tx
     */
    fun getTxOverheadSize(): Int

    /**
     * The size needed to fit anchor packet into special tx
     */
    fun calculateRequiredSize(packet: AnchoringPacket): Int

    /**
     * Adds anchoring packet to special tx
     */
    fun addAnchorPacket(packet: AnchoringPacket)

    fun build(): List<OpData>
}