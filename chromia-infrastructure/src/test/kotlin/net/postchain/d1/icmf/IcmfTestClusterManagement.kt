package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.d1.cluster.D1PeerInfo

class IcmfTestClusterManagement : ClusterManagement {
    companion object {
        private val cryptoSystem = Secp256K1CryptoSystem()
        val privKey = PrivKey(cryptoSystem.getRandomBytes(32))
        val pubKey = PubKey(secp256k1_derivePubKey(privKey.key))

        const val senderCluster = "senderCluster"
        const val receiverCluster = "receiverCluster"

        val anchorChainRid = BlockchainRid.buildRepeat(0)
        val senderOneChainRid = BlockchainRid.buildRepeat(1)
        val senderTwoChainRid = BlockchainRid.buildRepeat(2)
    }

    private val peers = listOf(
        D1PeerInfo("http://127.0.0.1:7740/", pubKey),
    )

    override fun getClusterNames() = listOf(senderCluster, receiverCluster)

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long) =
        peers.map { it.pubkey }

    override fun getClusterInfo(clusterName: String) =
        D1ClusterInfo(clusterName, anchorChainRid, peers)

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> {
        throw NotImplementedError("Not yet implemented")
    }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
        listOf(anchorChainRid, senderOneChainRid, senderTwoChainRid)

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String = when (blockchainRid) {
        anchorChainRid, senderOneChainRid -> senderCluster
        else -> receiverCluster
    }
}
