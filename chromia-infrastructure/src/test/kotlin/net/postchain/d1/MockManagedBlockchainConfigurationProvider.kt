package net.postchain.d1

import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider

class MockManagedBlockchainConfigurationProvider(private val clusterManagement: ClusterManagement) : BlockchainConfigProvider {
    override fun getRelevantPeers(headerData: BlockHeaderData): Collection<PubKey> {
        val blockchainRid = BlockchainRid(headerData.getBlockchainRid())
        val height = headerData.getHeight()
        return clusterManagement.getBlockchainPeers(blockchainRid, height)
    }
}
