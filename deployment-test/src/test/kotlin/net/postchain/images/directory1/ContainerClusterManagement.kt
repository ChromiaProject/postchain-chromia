package net.postchain.images.directory1

import net.postchain.common.BlockchainRid
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class ContainerClusterManagement(private val delegate: ClusterManagement, private val peers: Collection<D1PeerInfo>)
    : ClusterManagement by delegate {
    override fun getClusterInfo(clusterName: String): D1ClusterInfo =
            delegate.getClusterInfo(clusterName).copy(peers = peers)

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> = peers.map { it.restApiUrl }
}
