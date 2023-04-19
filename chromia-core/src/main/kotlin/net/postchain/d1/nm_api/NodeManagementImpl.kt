package net.postchain.d1.nm_api

import net.postchain.chromia.nm_api.NmGetBlockchainConfigurationV5Result
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationByHashResult
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationResult
import net.postchain.chromia.nm_api.nmGetBlockchainConfigurationV5
import net.postchain.chromia.nm_api.nmGetPendingBlockchainConfiguration
import net.postchain.chromia.nm_api.nmGetPendingBlockchainConfigurationByHash
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid

class NodeManagementImpl(private val query: PostchainQuery) : NodeManagement {

    override fun getPendingBlockchainConfigs(blockchainRid: BlockchainRid, height: Long): List<NmGetPendingBlockchainConfigurationResult> {
        return query.nmGetPendingBlockchainConfiguration(blockchainRid, height)
    }

    override fun getPendingBlockchainConfigByHash(blockchainRid: BlockchainRid, configHash: ByteArray): NmGetPendingBlockchainConfigurationByHashResult? {
        return query.nmGetPendingBlockchainConfigurationByHash(blockchainRid, configHash)
    }

    override fun getBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): NmGetBlockchainConfigurationV5Result? {
        return query.nmGetBlockchainConfigurationV5(blockchainRid, height)
    }
}