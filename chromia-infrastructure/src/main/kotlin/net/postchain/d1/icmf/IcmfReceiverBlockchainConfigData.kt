package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.toObject

data class IcmfReceiverBlockchainConfigData(
        @Name("global")
        @Nullable
        val global: IcmfReceiverTopicsAndSpecificBlockchainConfig?,

        @Name("local")
        @Nullable
        val local: List<IcmfReceiverSpecificBlockChainConfig>?,

        @Name("anchoring")
        @Nullable
        val anchoring: IcmfReceiverTopicsConfig?,

        @Name("directory-chain")
        @Nullable
        val directoryChain: IcmfReceiverTopicsConfig?,

        @Name("special-tx-margin-bytes")
        @DefaultValue(100 * 1024) // 100 KiB
        val specialTxMarginBytes: Long
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfReceiverBlockchainConfigData = gtv.toObject()
    }
}

data class IcmfReceiverTopicsAndSpecificBlockchainConfig(
        @Name("topics")
        @Nullable
        val topics: List<String>?,

        @Name("blockchains")
        @Nullable
        val blockchains: List<IcmfReceiverSpecificBlockChainConfig>?
)

data class IcmfReceiverSpecificBlockChainConfig(
        @Name("bc-rid")
        val blockchainRid: ByteArray,

        @Name("topic")
        val topic: String
)

data class IcmfReceiverTopicsConfig(
        @Name("topics")
        val topics: List<String>
)
