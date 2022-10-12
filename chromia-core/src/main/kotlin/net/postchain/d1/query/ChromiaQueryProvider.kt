package net.postchain.d1.query

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

interface ChromiaQueryProvider {
    fun getChain0Query(): (String, Gtv) -> Gtv
    fun getAnchorQuery(): ((String, Gtv) -> Gtv)?
    fun getQuery(blockchainRid: BlockchainRid): ((String, Gtv) -> Gtv)?
}
