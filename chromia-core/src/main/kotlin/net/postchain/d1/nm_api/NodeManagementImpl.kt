package net.postchain.d1.nm_api

import net.postchain.chromia.nm_api.NmGetBlockchainConfigurationV5Result
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationByHashResult
import net.postchain.chromia.nm_api.nmApiVersion
import net.postchain.chromia.nm_api.nmGetBlockchainConfigurationV5
import net.postchain.chromia.nm_api.nmGetManagementChain
import net.postchain.chromia.nm_api.nmGetPendingBlockchainConfigurationByHash
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid

class NodeManagementImpl(private val query: PostchainQuery) : NodeManagement {

    override fun nmApiVersion() = query.nmApiVersion()

    override fun getManagementChain() = BlockchainRid(query.nmGetManagementChain())

    override fun getPendingBlockchainConfigByHash(blockchainRid: BlockchainRid, configHash: ByteArray): NmGetPendingBlockchainConfigurationByHashResult? =
            query.nmGetPendingBlockchainConfigurationByHash(blockchainRid, configHash)

    override fun getBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): NmGetBlockchainConfigurationV5Result? =
            query.nmGetBlockchainConfigurationV5(blockchainRid, height)
}