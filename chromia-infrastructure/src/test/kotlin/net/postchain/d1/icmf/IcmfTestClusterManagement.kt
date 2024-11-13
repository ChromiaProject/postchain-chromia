package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class IcmfTestClusterManagement : ClusterManagement {
    companion object {
        private val cryptoSystem = Secp256K1CryptoSystem()
        val keyPair = cryptoSystem.generateKeyPair()

        const val restApiUrl = "http://127.0.0.1:7740/"

        const val senderCluster = "senderCluster"
        const val receiverCluster = "receiverCluster"

        val systemAnchoringChainRid = BlockchainRid.buildRepeat(0)
        val clusterAnchoringChainRid = BlockchainRid.buildRepeat(1)
        val remoteSenderChainRid = BlockchainRid.buildRepeat(2)
        val localSenderChainRid = BlockchainRid.buildRepeat(3)
        val receiverClusterAnchoringChainRid = BlockchainRid.buildRepeat(4)
        val localSenderChainRid2 = BlockchainRid.buildRepeat(5)
        val localSenderChainRid3 = BlockchainRid.buildRepeat(6)
    }

    private val peers = listOf(
            D1PeerInfo(restApiUrl, keyPair.pubKey),
    )

    override fun getClusterNames() = listOf(senderCluster, receiverCluster)

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long) =
            peers.map { it.pubkey }

    override fun getClusterInfo(clusterName: String) = when (clusterName) {
        senderCluster -> D1ClusterInfo(clusterName, clusterAnchoringChainRid, peers)
        receiverCluster -> D1ClusterInfo(clusterName, receiverClusterAnchoringChainRid, peers)
        else -> throw IllegalArgumentException(clusterName)
    }

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> = listOf(restApiUrl)

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
            listOf(systemAnchoringChainRid, clusterAnchoringChainRid, remoteSenderChainRid, localSenderChainRid)

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String = when (blockchainRid) {
        systemAnchoringChainRid, clusterAnchoringChainRid, remoteSenderChainRid -> senderCluster
        else -> receiverCluster
    }

    override fun getClusterAnchoringChains(): Collection<BlockchainRid> = listOf(clusterAnchoringChainRid)

    override fun getSystemAnchoringChain(): BlockchainRid = systemAnchoringChainRid
    override fun getRemovedClusterBlockchains(clusterName: String, removedAfter: Long): Collection<BlockchainRid> = listOf()
    override fun getRemovedClusterAnhcoringChains(removedAfter: Long): Collection<BlockchainRid> = listOf()
}
