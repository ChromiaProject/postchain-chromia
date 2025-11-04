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

        @Name("local-to-me")
        @Nullable
        val localToMe: List<IcmfReceiverSpecificBlockChainConfigWithoutSkipToHeight>?,

        @Name("anchoring")
        @Nullable
        val anchoring: IcmfReceiverTopicsConfig?,

        @Name("anchoring-to-me")
        @Nullable
        val anchoringToMe: IcmfReceiverTopicsConfig?,

        @Name("directory-chain")
        @Nullable
        val directoryChain: IcmfReceiverTopicsConfig?,

        @Name("directory-chain-to-me")
        @Nullable
        val directoryChainToMe: IcmfReceiverTopicsConfig?,

        @Name("special-tx-margin-bytes")
        @DefaultValue(100 * 1024) // 100 KiB
        val specialTxMarginBytes: Long,

        @Name("message-limit")
        @DefaultValue(1000)
        val messageLimit: Long
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
        val topic: String,

        @Name("skip-to-height")
        @DefaultValue(0)
        val skipToHeight: Long
)

data class IcmfReceiverSpecificBlockChainConfigWithoutSkipToHeight(
        @Name("bc-rid")
        val blockchainRid: ByteArray,

        @Name("topic")
        val topic: String,
)

data class IcmfReceiverTopicsConfig(
        @Name("topics")
        val topics: List<String>
)
