package net.postchain.mc.cli.node

import com.github.ajalt.clikt.core.CliktCommand
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.anchoring.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.chain0.common.queries.getBlockchainCluster
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.listClustersOfNode
import net.postchain.chain0.nm_api.nmComputeBlockchainInfoList
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.SingleEndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.mc.cli.requiredPubkeyOption
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.network.NodeVerifier

class CommandNodeVerify : CliktCommand(
        name = "verify",
        help = "Verify node status"
) {
    private val client by clientOption()

    private val key by requiredPubkeyOption()

    private fun Boolean?.isOk(): String = this?.let { if (this) "OK" else "Bad" } ?: "Bad"
    override fun run() {

        val node = client.getNodeData(key)
        val nodeVerifier = NodeVerifier(client.config, client.cmGetSystemAnchoringChain()?.let { BlockchainRid(it) })
        val clusters = client.listClustersOfNode(key)
        val clusterAnchorChains = clusters.map { client.cmGetClusterInfo(it) }.associate { it.name to BlockchainRid(it.anchoringChain) }
        val nodeStatus = nodeVerifier.verifyApi(node.apiUrl)

        table {
            row("Node", "${node.pubkey}")
            row("Url", "${node.apiUrl}")
            row("System chains", "${nodeStatus.responds.isOk()}")
            row("Management chain", "${nodeStatus.height}")
            row("System anchoring chain", "${nodeStatus.systemAnchorHeight}")
            row("Cluster anchor chains", "${clusterAnchorChains.map { it.key to nodeVerifier.verifyBlockchain(it.value, node.apiUrl).second }.joinToString(",")}")
            hints {
                defaultAlignment = Table.Hints.Alignment.LEFT
            }
        }.render().also { echo(it) }

        val blockchains = client.nmComputeBlockchainInfoList(key.data).filter { !it.system }.map { BlockchainRid(it.rid) }

        val bcStatuses = blockchains.associateWith { blockchainRid ->
            val bcHeight = nodeVerifier.verifyBlockchain(blockchainRid, node.apiUrl)
            val anchoredHeight = client.getBlockchainCluster(blockchainRid)?.let { bcCluster ->
                clusterAnchorChains[bcCluster]
            }?.let { anchoringBrid ->
                PostchainClientImpl(client.config.copy(
                        blockchainRid = anchoringBrid,
                        endpointPool = SingleEndpointPool(node.apiUrl)
                )).getLastAnchoredBlock(blockchainRid)?.blockHeight
            }

            Triple(anchoredHeight, bcHeight.first, bcHeight.second)
        }

        table {
            header("Blockchain", "Responds", "Height", "Anchored Height", "Synchronized")
            bcStatuses.forEach { (brid, status) ->
                row(
                        brid.toHex(),
                        "${status.second}",
                        "${status.third}",
                        "${status.first}",
                        status.third?.let { if (it < status.first ?: 0) "NO" else "Yes" } ?: "NO"
                )
            }
            hints {
                borderStyle = Table.BorderStyle.SINGLE_LINE
                defaultAlignment = Table.Hints.Alignment.LEFT
            }
        }.render().also { echo(it) }


    }
}
