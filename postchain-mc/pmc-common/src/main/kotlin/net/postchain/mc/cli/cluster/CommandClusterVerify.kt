package net.postchain.mc.cli.cluster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.required
import de.m3y.kformat.Table
import de.m3y.kformat.table
import net.postchain.anchoring.anchoring_chain_common.getLastAnchoredBlock
import net.postchain.chain0.cm_api.CmClusterInfo
import net.postchain.chain0.cm_api.cmGetClusterAnchoringChains
import net.postchain.chain0.cm_api.cmGetClusterBlockchains
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.cm_api.cmGetSystemAnchoringChain
import net.postchain.client.config.RequestStrategies
import net.postchain.client.exception.ClientError
import net.postchain.client.impl.PostchainClientImpl
import net.postchain.client.request.EndpointPool
import net.postchain.client.request.SingleEndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.mc.cli.util.clientOption
import net.postchain.mc.cli.util.nameOption
import java.time.Duration

class CommandClusterVerify : CliktCommand(
        name = "verify",
        help = "Verify cluster status"
) {
    private val client by clientOption()

    private val cluster by nameOption("name of cluster").required()

    override fun run() {
        val clusterInfo = client.cmGetClusterInfo(cluster)
        val clusterEndpoints = clusterInfo.peers.map { it.apiUrl }.let { EndpointPool.default(it) }

        echo("Verifying cluster $cluster")
        table {
            header("Pubkey", "Url")
            clusterInfo.peers.forEach { row(it.pubkey.toHex().substring(50), it.apiUrl) }
            hints {
                defaultAlignment = Table.Hints.Alignment.LEFT
            }
        }.render().also { echo(it) }

        val anchoringClient = PostchainClientImpl(client.config.copy(
                blockchainRid = BlockchainRid(clusterInfo.anchoringChain),
                endpointPool = clusterEndpoints,
                connectTimeout = Duration.ofMillis(300),
                responseTimeout = Duration.ofMillis(300),
                requestStrategy = RequestStrategies.TRY_NEXT_ON_ERROR.factory
        ))
        echo("Cluster Chains")
        analyzeBlockchains(client.cmGetClusterBlockchains(cluster), clusterInfo, anchoringClient)

        if (cluster == "system") {
            val systemAnchoringClient = PostchainClientImpl(
                    client.config.copy(
                            blockchainRid = BlockchainRid(client.cmGetSystemAnchoringChain()!!),
                            endpointPool = clusterEndpoints,
                            connectTimeout = Duration.ofMillis(300),
                            responseTimeout = Duration.ofMillis(300),
                            requestStrategy = RequestStrategies.TRY_NEXT_ON_ERROR.factory

                    )
            )
            echo("Anchoring Chains")
            analyzeBlockchains(client.cmGetClusterAnchoringChains(), clusterInfo, systemAnchoringClient)
        }

    }

    private fun analyzeBlockchains(chainsToAnalyze: Collection<ByteArray>, clusterInfo: CmClusterInfo, anchoringClient: PostchainClientImpl) {
        chainsToAnalyze.map { BlockchainRid(it) }.forEach { bc ->
            table {
                header("Blockchain", "Anchored height", *clusterInfo.peers.map { it.pubkey.toHex().substring(50) }.toTypedArray())

                val peerClients = clusterInfo.peers.map {
                    PostchainClientImpl(
                            client.config.copy(
                                    blockchainRid = bc,
                                    endpointPool = SingleEndpointPool(it.apiUrl),
                                    connectTimeout = Duration.ofMillis(300),
                                    responseTimeout = Duration.ofMillis(300),
                            )
                    )
                }

                row(
                        bc.toShortHex(),
                        getLastAnchoredBlockHeight(anchoringClient, bc)?.toString() ?: "",
                        *peerClients.map { getCurrentBlockHeight(it).toString() }.toTypedArray()
                )
                hints {
                    defaultAlignment = Table.Hints.Alignment.LEFT
                    borderStyle = Table.BorderStyle.SINGLE_LINE
                }
            }.render().also { echo(it) }
        }
    }

    private fun getCurrentBlockHeight(it: PostchainClientImpl) = try {
        it.currentBlockHeight()
    } catch (e: ClientError) {
        -1
    }

    private fun getLastAnchoredBlockHeight(anchoringClient: PostchainClientImpl, bc: BlockchainRid): Long? {
        return try {
            anchoringClient.getLastAnchoredBlock(bc)?.blockHeight
        } catch (e: ClientError) {
            -1
        }
    }


}
