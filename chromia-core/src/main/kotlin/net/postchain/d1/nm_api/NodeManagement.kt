package net.postchain.d1.nm_api

import net.postchain.chromia.nm_api.NmGetBlockchainConfigurationV5Result
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationByHashResult
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationResult
import net.postchain.common.BlockchainRid

interface NodeManagement {
    fun getPendingBlockchainConfigs(blockchainRid: BlockchainRid, height: Long): List<NmGetPendingBlockchainConfigurationResult>
    fun getPendingBlockchainConfigByHash(blockchainRid: BlockchainRid, configHash: ByteArray): NmGetPendingBlockchainConfigurationByHashResult?
    fun getBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): NmGetBlockchainConfigurationV5Result?
}