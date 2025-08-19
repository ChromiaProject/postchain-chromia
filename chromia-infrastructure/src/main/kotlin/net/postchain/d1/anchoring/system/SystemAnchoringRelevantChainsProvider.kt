package net.postchain.d1.anchoring.system

import mu.KLogging
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.d1.anchoring.RelevantChainsProvider
import net.postchain.d1.cluster.ClusterManagement

class SystemAnchoringRelevantChainsProvider(
        private val clusterManagement: ClusterManagement
) : RelevantChainsProvider {

    companion object : KLogging()

    override fun getRelevantChains(includeRemovedChainsSince: Long?) = try {
        clusterManagement.getClusterAnchoringChains().toSet()
    } catch (e: PmEngineIsAlreadyClosed) {
        logger.debug { "Could not fetch relevant chains due to a restart on chain ${e.chainId}" }
        emptySet()
    } catch (e: Exception) {
        logger.error("Could not fetch relevant chains", e)
        emptySet()
    }
}
