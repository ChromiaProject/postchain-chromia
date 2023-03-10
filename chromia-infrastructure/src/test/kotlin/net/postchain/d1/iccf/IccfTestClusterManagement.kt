package net.postchain.d1.iccf

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.devtools.utils.ChainUtil

class IccfTestClusterManagement : ClusterManagement {

    override fun getClusterNames(): Collection<String> {
        throw NotImplementedError()
    }

    override fun getClusterInfo(clusterName: String): D1ClusterInfo =
            D1ClusterInfo(clusterName, ChainUtil.ridOf(2), listOf())

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> {
        throw NotImplementedError()
    }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long) =
            listOf(
                    PubKey("03a301697bdfcd704313ba48e51d567543f2a182031efd6915ddc07bbcc4e16070"),
                    PubKey("031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"),
                    PubKey("03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94")
            )

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> = when (clusterName) {
        "clusterA" -> listOf(ChainUtil.ridOf(1L), ChainUtil.ridOf(2L), ChainUtil.ridOf(3L), ChainUtil.ridOf(4L))
        "clusterB" -> listOf(ChainUtil.ridOf(5L))
        else -> emptyList()
    }

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String = when (ChainUtil.iidOf(blockchainRid)) {
        1L, 2L, 3L, 4L -> "clusterA"
        5L -> "clusterB"
        else -> "unknown_cluster"
    }

    override fun getClusterAnchoringChains(): Collection<BlockchainRid> = listOf(ChainUtil.ridOf(2))

    override fun getSystemAnchoringChain(): BlockchainRid = ChainUtil.ridOf(1)

}
