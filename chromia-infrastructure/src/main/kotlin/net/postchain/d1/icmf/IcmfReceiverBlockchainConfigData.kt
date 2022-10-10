package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.toObject

data class IcmfReceiverBlockchainConfigData(
        @Name("global")
        @Nullable
        val global: IcmfReceiverGlobalConfig?,

        @Name("blockchains")
        @Nullable
        val blockchains: List<IcmfReceiverSpecificBlockChainConfig>?
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfReceiverBlockchainConfigData = gtv.toObject()
    }
}

data class IcmfReceiverGlobalConfig(
        @Name("topics")
        val topics: List<String>
)

data class IcmfReceiverSpecificBlockChainConfig(
        @Name("bc-rid")
        val blockchainRid: ByteArray,

        @Name("topic")
        val topic: String
)
