package net.postchain.d1.icmf

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.toObject

data class IcmfReceiverBlockchainConfigData(
        @param:Name("global")
        @param:Nullable
        val global: IcmfReceiverTopicsAndSpecificBlockchainConfig?,

        @param:Name("local")
        @param:Nullable
        val local: List<IcmfReceiverSpecificBlockChainConfig>?,

        @param:Name("local-to-me")
        @param:Nullable
        val localToMe: List<IcmfReceiverSpecificBlockChainConfigWithoutSkipToHeight>?,

        @param:Name("anchoring")
        @param:Nullable
        val anchoring: IcmfReceiverTopicsConfig?,

        @param:Name("anchoring-to-me")
        @param:Nullable
        val anchoringToMe: IcmfReceiverTopicsConfig?,

        @param:Name("directory-chain")
        @param:Nullable
        val directoryChain: IcmfReceiverTopicsConfig?,

        @param:Name("directory-chain-to-me")
        @param:Nullable
        val directoryChainToMe: IcmfReceiverTopicsConfig?,

        @param:Name("special-tx-margin-bytes")
        @param:DefaultValue(100 * 1024) // 100 KiB
        val specialTxMarginBytes: Long,

        @param:Name("message-limit")
        @param:DefaultValue(1000)
        val messageLimit: Long,

        @param:Name("message-check-interval-ms")
        @param:DefaultValue(defaultLong = 1000)
        val messageCheckInterval: Long,
) {
    companion object {
        fun fromGtv(gtv: Gtv): IcmfReceiverBlockchainConfigData = gtv.toObject()
    }
}

data class IcmfReceiverTopicsAndSpecificBlockchainConfig(
        @param:Name("topics")
        @param:Nullable
        val topics: List<String>?,

        @param:Name("blockchains")
        @param:Nullable
        val blockchains: List<IcmfReceiverSpecificBlockChainConfig>?
)

data class IcmfReceiverSpecificBlockChainConfig(
        @param:Name("bc-rid")
        val blockchainRid: ByteArray,

        @param:Name("topic")
        val topic: String,

        @param:Name("skip-to-height")
        @param:DefaultValue(0)
        val skipToHeight: Long
)

data class IcmfReceiverSpecificBlockChainConfigWithoutSkipToHeight(
        @param:Name("bc-rid")
        val blockchainRid: ByteArray,

        @param:Name("topic")
        val topic: String,
)

data class IcmfReceiverTopicsConfig(
        @param:Name("topics")
        val topics: List<String>
)
