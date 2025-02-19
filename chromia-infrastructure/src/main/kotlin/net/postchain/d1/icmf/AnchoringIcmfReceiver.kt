package net.postchain.d1.icmf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class AnchoringIcmfReceiver(
        private val topics: List<String>,
        private val clusterManagement: ClusterManagement,
        private val queryProvider: ChromiaQueryProvider
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val systemAnchoringPipes: ConcurrentMap<String, IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = ConcurrentHashMap()
    private val clusterAnchoringPipes: ConcurrentMap<Pair<String, String>, IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = ConcurrentHashMap()
    private val jobSynchronizer = Object()
    private var job: Job? = null
    private var systemAnchoringPipesCreated = false

    private fun start(): Job {
        return CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater") + MDCContext()) {
            val allClusters = clusterManagement.getClusterNames()
            if (allClusters.isNotEmpty()) {
                createSystemAnchoringPipes()
                createClusterAnchoringPipes(allClusters)
            }

            while (isActive) {
                delay(pollInterval)
                try {
                    logger.info("Updating set of clusters")
                    updateClusters()
                    logger.info("Updated set of clusters")
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error("Clusters update failed: ${e.message}", e)
                }
            }
        }
    }

    private fun updateClusters() {
        val currentClusters = clusterAnchoringPipes.keys.map { it.first }.toSet()
        val updatedClusters = clusterManagement.getClusterNames().toSet()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            for (topic in topics) {
                clusterAnchoringPipes.remove(clusterName to topic)?.shutdown()
            }
        }

        if (addedClusters.isNotEmpty()) {
            if (!systemAnchoringPipesCreated) {
                createSystemAnchoringPipes()
            }
            createClusterAnchoringPipes(addedClusters)
        }
    }

    private fun createClusterAnchoringPipes(clusterNames: Collection<String>) {
        for (clusterName in clusterNames) {
            for (topic in topics) {
                clusterAnchoringPipes[clusterName to topic] = run {
                    val blockchainRid = clusterManagement.getClusterInfo(clusterName).anchoringChain
                    val route = TopicRoute(topic, listOf())
                    IntraClusterTopicPipe(queryProvider, route, blockchainRid)
                }
            }
        }
    }

    private fun createSystemAnchoringPipes() {
        clusterManagement.getSystemAnchoringChain()?.let {
            for (topic in topics) {
                val route = TopicRoute(topic, listOf())
                systemAnchoringPipes[topic] = IntraClusterTopicPipe(queryProvider, route, it)
            }
        }
        systemAnchoringPipesCreated = true
    }

    override fun getRelevantPipes(): List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> {
        synchronized(jobSynchronizer) {
            if (job == null) {
                try {
                    job = start()
                } catch (e: Exception) {
                    logger.error("Failed to start receiver", e)
                    shutdown() // Clean up
                }
            }
        }
        return systemAnchoringPipes.values + clusterAnchoringPipes.values
    }

    override fun shutdown() {
        synchronized(jobSynchronizer) {
            job?.cancel()
        }
        for (pipe in systemAnchoringPipes.values + clusterAnchoringPipes.values) {
            pipe.shutdown()
        }
    }
}
