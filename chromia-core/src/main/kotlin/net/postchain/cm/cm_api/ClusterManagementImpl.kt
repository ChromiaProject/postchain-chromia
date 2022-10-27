package net.postchain.cm.cm_api

import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class ClusterManagementImpl(private val query: PostchainQuery) : ClusterManagement {
    override fun getClusterNames(): Collection<String> = query.cmGetClusterNames()

    override fun getClusterInfo(clusterName: String): D1ClusterInfo = query.cmGetClusterInfo(clusterName)
            .let {
                D1ClusterInfo(
                        it.name,
                        BlockchainRid(it.anchoringChain),
                        it.peers.map { peer -> D1PeerInfo(peer.apiUrl, peer.pubkey) })
            }

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> =
            query.cmGetBlockchainApiUrls(blockchainRid)

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<PubKey> =
            query.cmGetPeerInfo(blockchainRid.data, height).map { PubKey(it) }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
            query.cmGetClusterBlockchains(clusterName).map { BlockchainRid(it) }

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String =
            query.cmGetBlockchainCluster(blockchainRid.data)
}
