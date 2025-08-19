package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid

fun interface AnchoringPipeFactory {
    fun create(blockchainRid: BlockchainRid): AnchoringPipe?
}
