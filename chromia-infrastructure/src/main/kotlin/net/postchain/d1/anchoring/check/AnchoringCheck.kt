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
import net.postchain.d1.anchoring.AnchoringPipeManager
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.d1.anchoring.cluster.ClusterAnchoringRelevantChainsProvider
import net.postchain.d1.anchoring.evm.Web3jClient
import net.postchain.d1.anchoring.system.SystemAnchoringRelevantChainsProvider
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.GtvFactory.gtv
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class AnchoringCheck(private val nodeDiagnosticContext: NodeDiagnosticContext, private val blockQueriesProvider: BlockQueriesProvider, private val appConfig: AppConfig) {

    companion object : KLogging()

    val runningChainsBlockClients = ConcurrentHashMap<BlockchainRid, PostchainBlockClient>()

    private val cacToAnchoringPipeManager = ConcurrentHashMap<BlockchainRid, AnchoringPipeManager>()

    private var systemAnchoringBrid: BlockchainRid? = null
    private var cacCheckJob: Job? = null
    private var sacCheckJob: Job? = null
    private var evmCheckJob: Job? = null

    fun maybeCreateAnchoringCheckCronJob(anchoringSpecialTxExtension: AnchoringSpecialTxExtension, anchorChainRid: BlockchainRid, anchorBlockQueries: BlockQueries, queries: Set<String>) {
        if (queries.contains("get_anchor_block_by_transaction_block_height")) {
            val anchoringCheckConfig = AnchoringCheckConfig.fromAppConfig(appConfig)
            val isClusterAnchoringChain = anchoringSpecialTxExtension.anchoringPipeManager.relevantChainsProvider is ClusterAnchoringRelevantChainsProvider
            if (isClusterAnchoringChain && anchoringCheckConfig.clusterAnchorCheckIntervalMs >= 0) {
                cacToAnchoringPipeManager[anchorChainRid] = anchoringSpecialTxExtension.anchoringPipeManager
                val currentJob = cacCheckJob
                if (currentJob == null) {
                    cacCheckJob = checkHighestBlockHeightsAnchoredInCAC(anchoringCheckConfig.clusterAnchorCheckIntervalMs)
                }
            }
            val isSystemAnchoringChain = anchoringSpecialTxExtension.anchoringPipeManager.relevantChainsProvider is SystemAnchoringRelevantChainsProvider
            if (isSystemAnchoringChain) {
                systemAnchoringBrid = anchorChainRid
                if (anchoringCheckConfig.systemAnchorCheckIntervalMs >= 0) {
                    sacCheckJob = checkHighestBlockHeightsAnchoredInSAC(anchorBlockQueries, anchoringCheckConfig.systemAnchorCheckIntervalMs)
                }
                if (anchoringCheckConfig.evmAnchorCheckIntervalMs >= 0) {
                    if (anchoringCheckConfig.rpcUrls.isNotEmpty() && anchoringCheckConfig.anchoringContractAddress.isNotEmpty()) {
                        evmCheckJob = checkHighestBlockHeightsAnchoredInEVM(anchorChainRid, anchorBlockQueries, anchoringCheckConfig)
                    } else {
                        logger.error("EVM anchoring check is not possible due to missing configuration, rpcUrls: ${anchoringCheckConfig.rpcUrls} , anchoringContractAddress: ${anchoringCheckConfig.anchoringContractAddress}")
                    }
                }
            }
        }
    }

    private fun checkHighestBlockHeightsAnchoredInCAC(verifyLastAnchoredHeightIntervalMs: Long) =
            CoroutineScope(Dispatchers.IO).launch(CoroutineName("check-cluster-anchoring") + MDCContext()) {
                while (isActive) {
                    try {
                        val cacChecks = mutableMapOf<BlockchainRid, AnchoringChainCheck>()
                        for (clusterAnchorChainRid in cacToAnchoringPipeManager.keys) {
                            val blockchains = getBlockchainRids(clusterAnchorChainRid)
                            val cacBlockQueries = blockQueriesProvider.getBlockQueries(clusterAnchorChainRid)
                            if (cacBlockQueries != null) {
                                val lastCacBlockHeight = cacBlockQueries.getLastBlockHeight().get()
                                if (lastCacBlockHeight >= 0) {
                                    cacChecks.putAll(blockchains.associateWith { checkBlockHeightAnchoredInCAC(cacBlockQueries, it, lastCacBlockHeight) })
                                }
                            }
                        }
                        nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK] = EagerDiagnosticValue(cacChecks)
                        delay(verifyLastAnchoredHeightIntervalMs)
                    } catch (_: CancellationException) {
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
                    } catch (_: CancellationException) {
                        break
                    } catch (e: Exception) {
                        logger.error("Unable to check last system anchored height with error: ${e.message}", e)
                        delay(verifyLastSystemAnchoredHeightInterval)
                    }
                }
            }

    private fun checkHighestBlockHeightsAnchoredInEVM(systemAnchoringBrid: BlockchainRid, systemAnchoringBlockQueries: BlockQueries, config: AnchoringCheckConfig) =
            CoroutineScope(Dispatchers.IO).launch(CoroutineName("check-EVM-anchoring") + MDCContext()) {
                val web3jClient = Web3jClient(config.rpcUrls, systemAnchoringBrid)
                while (isActive) {
                    try {
                        val evmChecks = checkHighestBlockHeightsAnchoredInEVM(web3jClient, config.anchoringContractAddress, systemAnchoringBlockQueries)
                        nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_EVM_ANCHORING_CHECK] = EagerDiagnosticValue(evmChecks)
                        delay(config.evmAnchorCheckIntervalMs)
                    } catch (_: CancellationException) {
                        web3jClient.close()
                        break
                    } catch (e: Exception) {
                        logger.error("Unable to check last EVM anchored height with error: ${e.message}", e)
                        delay(config.evmAnchorCheckIntervalMs)
                    }
                }
            }

    private fun getBlockchainRids(clusterAnchorChainRid: BlockchainRid): Set<BlockchainRid> {
        return cacToAnchoringPipeManager[clusterAnchorChainRid]?.relevantChainsProvider?.getRelevantChains()
                ?.minus(clusterAnchorChainRid)
                ?.toSet()
                ?.intersect(runningChainsBlockClients.keys)
                ?: setOf()
    }

    private fun getBlockchainRids(): Set<BlockchainRid> {
        return cacToAnchoringPipeManager.values.flatMap { it.relevantChainsProvider.getRelevantChains() }.minus(cacToAnchoringPipeManager.keys).toSet()
    }

    private fun checkBlockHeightAnchoredInCAC(clusterAnchorBlockQueries: BlockQueries, blockchainRid: BlockchainRid, cacBlockHeight: Long): AnchoringChainCheck {
        val bcBlockClient = runningChainsBlockClients[blockchainRid]
                ?: return AnchoringChainCheck(error = "Unable to get block client for blockchain")
        var error: String
        try {
            val bcBlockAnchorInCac = clusterAnchorBlockQueries.query("get_anchor_block_by_transaction_block_height", gtv(mapOf("blockchain_rid" to gtv(blockchainRid.data), Pair("transaction_block_height", gtv(cacBlockHeight))))).get()
            error = if (!bcBlockAnchorInCac.isNull()) {
                val bcBlockHeightAnchoredInCac = bcBlockAnchorInCac["block_height"]?.asInteger()
                val bcBlockAtHeight = bcBlockClient.blockAtHeight(bcBlockHeightAnchoredInCac!!)
                if (bcBlockAtHeight != null) {
                    val match = bcBlockAtHeight.rid.data.contentEquals(bcBlockAnchorInCac["block_rid"]?.asByteArray())
                    return AnchoringChainCheck(bcBlockHeightAnchoredInCac, match)
                } else {
                    "Blockchain is behind CAC last anchored block."
                }
            } else {
                "Cluster anchor block could not be retrieved."
            }
        } catch (e: Exception) {
            logger.error("Unable to check if blockchain $blockchainRid is anchored in CAC with error: ${e.message}", e)
            error = "Unable to check if blockchain is anchored in CAC."
        }
        return AnchoringChainCheck(error = error)
    }

    private fun checkBlockHeightsAnchoredInSAC(systemAnchorBlockQueries: BlockQueries, sacBlockHeight: Long): Map<BlockchainRid, AnchoringChainCheck> {
        val result = mutableMapOf<BlockchainRid, AnchoringChainCheck>()
        for (clusterAnchorChainRid in cacToAnchoringPipeManager.keys) {
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
            error = if (sacBlockAtHeight != null) {
                val sacBlockRID = sacBlockAtHeight.header.blockRID
                val sacBlockRidMatchesBlockRidAnchoredInEvm = sacBlockRID.contentEquals(sacBlockRidAnchoredInEvm)
                if (sacBlockRidMatchesBlockRidAnchoredInEvm) {
                    return checkBlockHeightsAnchoredInSAC(systemAnchorBlockQueries, sacHeightAnchoredInEvm)
                } else {
                    "SAC anchor block does not match the block anchored in EVM."
                }
            } else {
                "SAC is behind EVM last anchored block."
            }
        } catch (e: Exception) {
            logger.error("Unable to check if SAC is anchored in EVM with error: ${e.message}", e)
            error = "Unable to check if blockchain is anchored."
        }
        return getBlockchainRids().associateWith { AnchoringChainCheck(error = error) }
    }

    fun remove(blockchainRid: BlockchainRid) {
        if (blockchainRid == systemAnchoringBrid) {
            sacCheckJob?.cancel()
            evmCheckJob?.cancel()
        } else {
            cacToAnchoringPipeManager.remove(blockchainRid)?.also {
                if (cacToAnchoringPipeManager.isEmpty()) {
                    cacCheckJob?.cancel()
                    cacCheckJob = null
                }
            }
        }
    }
}