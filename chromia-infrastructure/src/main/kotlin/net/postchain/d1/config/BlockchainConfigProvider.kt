package net.postchain.d1.config

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.crypto.PubKey

fun interface BlockchainConfigProvider {
    fun getRelevantPeers(headerData: BlockHeaderData): List<PubKey>
}