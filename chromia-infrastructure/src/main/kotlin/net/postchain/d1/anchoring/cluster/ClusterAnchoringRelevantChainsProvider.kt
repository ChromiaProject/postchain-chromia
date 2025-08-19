package net.postchain.d1.anchoring.cluster

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.d1.anchoring.RelevantChainsProvider
import net.postchain.d1.cluster.ClusterManagement

class ClusterAnchoringRelevantChainsProvider(
        private val cluster: String,
        private val clusterManagement: ClusterManagement,
        private val systemAnchoringChain: BlockchainRid?,
        private val anchoringBlockchainRid: BlockchainRid
) : RelevantChainsProvider {

    companion object : KLogging()

    override fun getRelevantChains(includeRemovedChainsSince: Long?) = try {
        val activeClusterChains = (if (includeRemovedChainsSince != null) {
            clusterManagement.getActiveBlockchains(cluster) + clusterManagement.getRemovedClusterBlockchains(cluster, includeRemovedChainsSince)
        } else clusterManagement.getActiveBlockchains(cluster)).toSet()
        if (systemAnchoringChain != null) {
            activeClusterChains - anchoringBlockchainRid - systemAnchoringChain
        } else {
            activeClusterChains - anchoringBlockchainRid
        }
    } catch (e: PmEngineIsAlreadyClosed) {
        logger.debug { "Could not fetch relevant chains due to a restart on chain ${e.chainId}" }
        emptySet()
    } catch (e: Exception) {
        logger.error("Could not fetch relevant chains", e)
        emptySet()
    }
}
