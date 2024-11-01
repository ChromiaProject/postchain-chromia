package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid

data class LocalIcmfOrigin(
        val topic: String,
        val blockchainRid: BlockchainRid,
        val skipToHeight: Long = 0
)