package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable

data class IcmfReceiverTopicEventMessage(
        val topic: String,

        @Nullable
        @Name("bc_rid")
        val bcRid: ByteArray?,

        @Name("skip_to_height")
        @DefaultValue(0)
        val skipToHeight: Long
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfReceiverTopicEventMessage =
                GtvObjectMapper.fromGtv(gtv, IcmfReceiverTopicEventMessage::class.java)
    }
}
