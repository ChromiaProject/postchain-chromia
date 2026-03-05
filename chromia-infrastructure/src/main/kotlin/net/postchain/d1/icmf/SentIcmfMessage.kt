package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

data class SentIcmfMessage(
        val topic: String, // Topic of message
        val body: Gtv,
        val receiver: BlockchainRid?,
) {

    companion object {
        fun fromGtv(gtv: Gtv): SentIcmfMessage =
                SentIcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!, gtv["receiver"]?.asByteArray()?.let { BlockchainRid(it) })
    }
}
