package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.toObject

const val DEFAULT_MESSAGE_QUERY_LIMIT = 100

data class IcmfSenderBlockchainConfigData(
        @Name("message_query_limit")
        @DefaultValue(DEFAULT_MESSAGE_QUERY_LIMIT.toLong())
        val messageQueryLimit: Long
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfSenderBlockchainConfigData = gtv.toObject()
    }
}
