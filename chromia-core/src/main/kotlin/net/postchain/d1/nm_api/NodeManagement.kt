package net.postchain.d1.nm_api

import net.postchain.chromia.nm_api.NmGetBlockchainConfigurationV5Result
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationByHashResult
import net.postchain.common.BlockchainRid

interface NodeManagement {
    fun nmApiVersion(): Long
    fun getManagementChain(): BlockchainRid
    fun getPendingBlockchainConfigByHash(blockchainRid: BlockchainRid, configHash: ByteArray): NmGetPendingBlockchainConfigurationByHashResult?
    fun getBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): NmGetBlockchainConfigurationV5Result?
}