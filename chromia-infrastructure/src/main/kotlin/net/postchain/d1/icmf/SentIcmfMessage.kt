package net.postchain.d1.icmf

import net.postchain.gtv.Gtv

data class SentIcmfMessage(
        val topic: String, // Topic of message
        val body: Gtv
) {

    companion object {
        fun fromGtv(gtv: Gtv): SentIcmfMessage =
                SentIcmfMessage(gtv["topic"]!!.asString(), gtv["body"]!!)
    }
}
