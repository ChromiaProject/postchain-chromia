package net.postchain.d1.cluster

import net.postchain.chain0.common.queries.*
import net.postchain.cm.ClusterManagementClient
import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv

class ClusterManagementImpl(query: (String, Gtv) -> Gtv) : ClusterManagement {
    private val cmApi = ClusterManagementClient(query)

    override fun getClusterNames(): Collection<String> = cmApi.cmGetClusterNames()

    override fun getClusterInfo(clusterName: String): D1ClusterInfo = cmApi.cmGetClusterInfo(clusterName)
        .let {
            D1ClusterInfo(
                it.name,
                BlockchainRid(it.anchoringChain),
                it.peers.map { peer -> D1PeerInfo(peer.apiUrl, PubKey(peer.pubkey)) })
        }

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> =
        cmApi.getBlockchainApiUrls(blockchainRid.data)

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<PubKey> =
        cmApi.cmGetPeerInfo(blockchainRid.data, height).map { PubKey(it) }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
        cmApi.cmGetClusterBlockchains(clusterName).map { BlockchainRid(it) }

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String =
        cmApi.cmGetBlockchainCluster(blockchainRid.data)
}
