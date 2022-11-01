package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid

interface ChromiaQueryProvider {
    fun getChain0Query(): PostchainQuery
    fun getAnchorQuery(): PostchainQuery?
    fun getQuery(blockchainRid: BlockchainRid): PostchainQuery?
}
