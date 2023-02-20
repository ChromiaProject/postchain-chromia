package net.postchain.d1.anchoring

import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.PubKey
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.D1ClusterInfo
import net.postchain.devtools.utils.ChainUtil

class AnchoringTestClusterManagement : ClusterManagement {
    override fun getClusterInfo(clusterName: String): D1ClusterInfo {
        throw NotImplementedError("Not yet implemented")
    }

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> {
        throw NotImplementedError("Not yet implemented")
    }

    override fun getClusterNames(): Collection<String> {
        throw NotImplementedError("Not yet implemented")
    }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<PubKey> {
        return listOf(
            PubKey("03a301697bdfcd704313ba48e51d567543f2a182031efd6915ddc07bbcc4e16070".hexStringToByteArray()),
            PubKey("031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F".hexStringToByteArray()),
            PubKey("03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94".hexStringToByteArray())
        )
    }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> {
        return when (clusterName) {
            "clusterA" -> listOf(ChainUtil.ridOf(1L), ChainUtil.ridOf(2L))
            "clusterB" -> listOf(ChainUtil.ridOf(3L))
            else -> emptyList()
        }
    }

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String {
        return when (ChainUtil.iidOf(blockchainRid)) {
            1L, 2L -> "clusterA"
            3L -> "clusterB"
            else -> "unknown_cluster"
        }
    }
}
