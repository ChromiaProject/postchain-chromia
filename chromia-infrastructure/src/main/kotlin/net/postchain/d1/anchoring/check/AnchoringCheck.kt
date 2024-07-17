package net.postchain.d1.anchoring.check

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import mu.KLogging
import net.postchain.client.core.PostchainBlockClient
import net.postchain.common.BlockchainRid
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.concurrent.util.get
import net.postchain.config.app.AppConfig
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.d1.anchoring.AnchoringReceiver
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.d1.anchoring.cluster.ClusterAnchoringReceiver
import net.postchain.d1.anchoring.evm.Web3jClient
import net.postchain.d1.anchoring.system.SystemAnchoringReceiver
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.GtvFactory.gtv
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class AnchoringCheck(private val nodeDiagnosticContext: NodeDiagnosticContext, private val blockQueriesProvider: BlockQueriesProvider, private val appConfig: AppConfig, private val anchoringCheckJobs: MutableMap<BlockchainRid, MutableList<Job>> = mutableMapOf()) {

    companion object : KLogging()

    val runningChainsBlockClients = ConcurrentHashMap<BlockchainRid, PostchainBlockClient>()

    private var cacToAnchoringReceiver = ConcurrentHashMap<BlockchainRid, AnchoringReceiver>()

    fun maybeCreateAnchoringCheckCronJob(anchoringSpecialTxExtension: AnchoringSpecialTxExtension, anchorChainRid: BlockchainRid, anchorBlockQueries: BlockQueries, queries: Set<String>) {
        if (queries.contains("get_anchor_block_by_transaction_block_height")) {
            val anchoringCheckConfig = AnchoringCheckConfig.fromAppConfig(appConfig)
            val isClusterAnchoringReceiver = anchoringSpecialTxExtension.anchoringReceiver is ClusterAnchoringReceiver
            if (isClusterAnchoringReceiver && anchoringCheckConfig.clusterAnchorCheckIntervalMs >= 0) {
                cacToAnchoringReceiver[anchorChainRid] = anchoringSpecialTxExtension.anchoringReceiver
                anchoringCheckJobs[anchorChainRid] = mutableListOf(checkHighestBlockHeightsAnchoredInCAC(anchorChainRid, anchorBlockQueries, anchoringCheckConfig.clusterAnchorCheckIntervalMs))
            }
            val isSystemAnchoringReceiver = anchoringSpecialTxExtension.anchoringReceiver is SystemAnchoringReceiver
            if (isSystemAnchoringReceiver && anchoringCheckConfig.systemAnchorCheckIntervalMs >= 0) {
                anchoringCheckJobs.getOrPut(anchorChainRid, ::mutableListOf) += checkHighestBlockHeightsAnchoredInSAC(anchorBlockQueries, anchoringCheckConfig.systemAnchorCheckIntervalMs)
            }
            if (isSystemAnchoringReceiver && anchoringCheckConfig.evmAnchorCheckIntervalMs >= 0) {
                if (anchoringCheckConfig.rpcUrls.isNotEmpty() && anchoringCheckConfig.anchoringContractAddress.isNotEmpty()) {
                    anchoringCheckJobs.getOrPut(anchorChainRid, ::mutableListOf) += checkHighestBlockHeightsAnchoredInEVM(anchorBlockQueries, anchoringCheckConfig)
                } else {
                    logger.error("EVM anchoring check is not possible due to missing configuration, rpcUrls: ${anchoringCheckConfig.rpcUrls} , anchoringContractAddress: ${anchoringCheckConfig.anchoringContractAddress}")
                }
            }
        }
    }

    private fun checkHighestBlockHeightsAnchoredInCAC(clusterAnchorChainRid: BlockchainRid, clusterAnchorBlockQueries: BlockQueries, verifyLastAnchoredHeightIntervalMs: Long) =
            CoroutineScope(Dispatchers.IO).launch(CoroutineName("check-cluster-anchoring-$clusterAnchorChainRid") + MDCContext()) {
                while (isActive) {
                    try {
                        val blockchains = getBlockchainRids(clusterAnchorChainRid)
                        val lastCacBlockHeight = clusterAnchorBlockQueries.getLastBlockHeight().get()
                        if (lastCacBlockHeight >= 0) {
                            val cacChecks = blockchains.associateWith { checkBlockHeightAnchoredInCAC(clusterAnchorBlockQueries, it, lastCacBlockHeight) }
                            nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK] = EagerDiagnosticValue(cacChecks)
                        }
                        delay(verifyLastAnchoredHeightIntervalMs)
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        logger.error("Unable to check last cluster anchored height with error: ${e.message}", e)
                        delay(verifyLastAnchoredHeightIntervalMs)
                    }
                }
            }

    private fun checkHighestBlockHeightsAnchoredInSAC(systemAnchorBlockQueries: BlockQueries, verifyLastSystemAnchoredHeightInterval: Long) =
            CoroutineScope(Dispatchers.IO).launch(CoroutineName("check-system-anchoring") + MDCContext()) {
                while (isActive) {
                    try {
                        val lastSacBlockHeight = systemAnchorBlockQueries.getLastBlockHeight().get()
                        if (lastSacBlockHeight >= 0) {
                            val sacChecks = checkBlockHeightsAnchoredInSAC(systemAnchorBlockQueries, lastSacBlockHeight)
                            nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_SYSTEM_ANCHORING_CHECK] = EagerDiagnosticValue(sacChecks)
                        }
                        delay(verifyLastSystemAnchoredHeightInterval)
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        logger.error("Unable to check last system anchored height with error: ${e.message}", e)
                        delay(verifyLastSystemAnchoredHeightInterval)
                    }
                }
            }

    private fun checkHighestBlockHeightsAnchoredInEVM(systemAnchoringBlockQueries: BlockQueries, config: AnchoringCheckConfig) =
            CoroutineScope(Dispatchers.IO).launch(CoroutineName("check-EVM-anchoring") + MDCContext()) {
                val web3jClient = Web3jClient(config.rpcUrls)
                while (isActive) {
                    try {
                        val evmChecks = checkHighestBlockHeightsAnchoredInEVM(web3jClient, config.anchoringContractAddress, systemAnchoringBlockQueries)
                        nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_EVM_ANCHORING_CHECK] = EagerDiagnosticValue(evmChecks)
                        delay(config.evmAnchorCheckIntervalMs)
                    } catch (e: CancellationException) {
                        web3jClient.close()
                        break
                    } catch (e: Exception) {
                        logger.error("Unable to check last EVM anchored height with error: ${e.message}", e)
                        delay(config.evmAnchorCheckIntervalMs)
                    }
                }
            }

    private fun getBlockchainRids(clusterAnchorChainRid: BlockchainRid): Set<BlockchainRid> {
        return cacToAnchoringReceiver[clusterAnchorChainRid]?.getRelevantChains()
                ?.minus(clusterAnchorChainRid)
                ?.toSet()
                ?.intersect(runningChainsBlockClients.keys)
                ?: setOf()
    }

    private fun getBlockchainRids(): Set<BlockchainRid> {
        return cacToAnchoringReceiver.values.flatMap { it.getRelevantChains() }.minus(cacToAnchoringReceiver.keys).toSet()
    }

    private fun checkBlockHeightAnchoredInCAC(clusterAnchorBlockQueries: BlockQueries, blockchainRid: BlockchainRid, cacBlockHeight: Long): AnchoringChainCheck {
        val bcBlockClient = runningChainsBlockClients[blockchainRid]
                ?: return AnchoringChainCheck(error = "Unable to get block client for blockchain")
        var error: String
        try {
            val bcBlockAnchorInCac = clusterAnchorBlockQueries.query("get_anchor_block_by_transaction_block_height", gtv(mapOf("blockchain_rid" to gtv(blockchainRid.data), Pair("transaction_block_height", gtv(cacBlockHeight))))).get()
            if (!bcBlockAnchorInCac.isNull()) {
                val bcBlockHeightAnchoredInCac = bcBlockAnchorInCac["block_height"]?.asInteger()
                val bcBlockAtHeight = bcBlockClient.blockAtHeight(bcBlockHeightAnchoredInCac!!)
                if (bcBlockAtHeight != null) {
                    val match = bcBlockAtHeight.rid.data.contentEquals(bcBlockAnchorInCac["block_rid"]?.asByteArray())
                    return AnchoringChainCheck(bcBlockHeightAnchoredInCac, match)
                } else {
                    error = "Blockchain is behind CAC last anchored block."
                }
            } else {
                error = "Cluster anchor block could not be retrieved."
            }
        } catch (e: Exception) {
            logger.error("Unable to check if blockchain $blockchainRid is anchored in CAC with error: ${e.message}", e)
            error = "Unable to check if blockchain is anchored in CAC."
        }
        return AnchoringChainCheck(error = error)
    }

    private fun checkBlockHeightsAnchoredInSAC(systemAnchorBlockQueries: BlockQueries, sacBlockHeight: Long): Map<BlockchainRid, AnchoringChainCheck> {
        val result = mutableMapOf<BlockchainRid, AnchoringChainCheck>()
        for (clusterAnchorChainRid in cacToAnchoringReceiver.keys) {
            val blockchains = getBlockchainRids(clusterAnchorChainRid)
            var error = ""
            try {
                val cacBlockAnchoredInSac = systemAnchorBlockQueries.query("get_anchor_block_by_transaction_block_height", gtv(mapOf("blockchain_rid" to gtv(clusterAnchorChainRid.data), Pair("transaction_block_height", gtv(sacBlockHeight))))).get()
                if (!cacBlockAnchoredInSac.isNull()) {
                    val cacBlockHeightAnchoredInSac = cacBlockAnchoredInSac["block_height"]?.asInteger()
                    val clusterAnchorBlockQueries = blockQueriesProvider.getBlockQueries(clusterAnchorChainRid)!!
                    val cacBlockAtHeight = clusterAnchorBlockQueries.getBlockAtHeight(cacBlockHeightAnchoredInSac!!, false).get()
                    if (cacBlockAtHeight != null) {
                        val cacBlockRidMatchesBlockRidAnchoredInSac = cacBlockAtHeight.header.blockRID.contentEquals(cacBlockAnchoredInSac["block_rid"]?.asByteArray())
                        if (cacBlockRidMatchesBlockRidAnchoredInSac) {
                            result.putAll(blockchains.associateWith { checkBlockHeightAnchoredInCAC(clusterAnchorBlockQueries, it, cacBlockHeightAnchoredInSac) })
                        } else {
                            error = "CAC block does not match the block anchored in SAC."
                        }
                    } else {
                        error = "CAC is behind SAC last anchored block."
                    }
                } else {
                    error = "System anchor block could not be retrieved."
                }
            } catch (e: Exception) {
                logger.error("Unable to check if blockchain SAC is anchored in CAC with error: ${e.message}", e)
                error = "Unable to check if blockchain is anchored in SAC."
            }
            if (error.isNotBlank()) {
                result.putAll(blockchains.associateWith { AnchoringChainCheck(error = error) })
            }
        }
        return result
    }

    private fun checkHighestBlockHeightsAnchoredInEVM(web3jClient: Web3jClient, anchoringContractAddresses: String, systemAnchorBlockQueries: BlockQueries): Map<BlockchainRid, AnchoringChainCheck> {
        var error: String
        try {
            val lastAnchoredBlock = web3jClient.withAnchoring(anchoringContractAddresses) {
                it.getLastAnchoredBlock()
            }
            val sacHeightAnchoredInEvm = lastAnchoredBlock.component1().value.longValueExact()
            val sacBlockRidAnchoredInEvm = lastAnchoredBlock.component2().value
            val sacBlockAtHeight = systemAnchorBlockQueries.getBlockAtHeight(sacHeightAnchoredInEvm, false).get()
            if (sacBlockAtHeight != null) {
                val sacBlockRID = sacBlockAtHeight.header.blockRID
                val sacBlockRidMatchesBlockRidAnchoredInEvm = sacBlockRID.contentEquals(sacBlockRidAnchoredInEvm)
                if (sacBlockRidMatchesBlockRidAnchoredInEvm) {
                    return checkBlockHeightsAnchoredInSAC(systemAnchorBlockQueries, sacHeightAnchoredInEvm)
                } else {
                    error = "SAC anchor block does not match the block anchored in EVM."
                }
            } else {
                error = "SAC is behind EVM last anchored block."
            }
        } catch (e: Exception) {
            logger.error("Unable to check if SAC is anchored in EVM with error: ${e.message}", e)
            error = "Unable to check if blockchain is anchored."
        }
        return getBlockchainRids().associateWith { AnchoringChainCheck(error = error) }
    }

    fun remove(blockchainRid: BlockchainRid) {
        anchoringCheckJobs.remove(blockchainRid)?.forEach { it.cancel() }
        cacToAnchoringReceiver.remove(blockchainRid)
    }
}