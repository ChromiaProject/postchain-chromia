package net.postchain.d1.query

import net.postchain.client.core.PostchainQuery
import net.postchain.client.core.PostchainReadClient
import net.postchain.common.BlockchainRid

interface ChromiaQueryProvider {
    fun getChain0Query(): PostchainQuery
    fun getAnchorQuery(): PostchainReadClient?
    fun getQuery(blockchainRid: BlockchainRid): PostchainReadClient?
}
