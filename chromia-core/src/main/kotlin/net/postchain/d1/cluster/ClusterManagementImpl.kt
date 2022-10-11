package net.postchain.d1.cluster

import net.postchain.common.BlockchainRid
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject

class ClusterManagementImpl(private val query: (String, Gtv) -> Gtv) : ClusterManagement {

    override fun getClusterNames(): Collection<String> =
            query("cm_get_cluster_names", gtv(mapOf())).asArray().map { it.asString() }

    override fun getClusterInfo(clusterName: String): D1ClusterInfo =
            query("cm_get_cluster_info", gtv(mapOf("name" to gtv(clusterName)))).toObject()

    override fun getBlockchainApiUrls(blockchainRid: BlockchainRid): Collection<String> =
            query("get_blockchain_api_urls", gtv(mapOf("blockchain_rid" to gtv(blockchainRid)))).asArray()
                    .map { it.asString() }

    override fun getBlockchainPeers(blockchainRid: BlockchainRid, height: Long): Collection<PubKey> =
            query(
                    "cm_get_peer_info", gtv(
                    mapOf(
                            "brid" to gtv(blockchainRid),
                            "height" to gtv(height)
                    )
            )
            ).asArray().map { PubKey(it.asByteArray()) }

    override fun getActiveBlockchains(clusterName: String): Collection<BlockchainRid> =
            query(
                    "cm_get_cluster_blockchains", gtv(
                    mapOf(
                            "name" to gtv(clusterName)
                    )
            )
            ).asArray().map { BlockchainRid(it.asByteArray()) }

    override fun getClusterOfBlockchain(blockchainRid: BlockchainRid): String =
            query(
                    "cm_get_blockchain_cluster", gtv(
                    mapOf(
                            "brid" to gtv(blockchainRid)
                    )
            )
            ).asString()
}
