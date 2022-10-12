package net.postchain.d1.query

import net.postchain.common.BlockchainRid
import net.postchain.managed.query.QueryRunner

interface ChromiaQueryProvider {
    fun getChain0Query(): QueryRunner
    fun getAnchorQuery(): QueryRunner?
    fun getQuery(blockchainRid: BlockchainRid): QueryRunner?
}
