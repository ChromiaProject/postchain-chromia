package net.postchain.d1.icmf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        private val queryProvider: ChromiaQueryProvider,
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val systemAnchoringPipes: ConcurrentMap<String, IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = ConcurrentHashMap()
    private val clusterAnchoringPipes: ConcurrentMap<Pair<String, String>, IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = ConcurrentHashMap()
    private val jobSynchronizer = Object()
    private var job: Job? = null

    private fun start(): Job {
        val allClusters = clusterManagement.getClusterNames()
        val systemAnchoringChain = clusterManagement.getSystemAnchoringChain()
        for (topic in topics) {
            systemAnchoringChain?.let {
                val route = TopicRoute(topic, listOf())
                systemAnchoringPipes[topic] = IntraClusterTopicPipe(queryProvider, route, it)
            }
            for (clusterName in allClusters) {
                clusterAnchoringPipes[clusterName to topic] = run {
                    val blockchainRid = clusterManagement.getClusterInfo(clusterName).anchoringChain
                    val route = TopicRoute(topic, listOf())
                    IntraClusterTopicPipe(queryProvider, route, blockchainRid)
                }
            }
        }

        return CoroutineScope(Dispatchers.IO).launch(CoroutineName("clusters-updater")) {
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
        for (clusterName in addedClusters) {
            for (topic in topics) {
                clusterAnchoringPipes[clusterName to topic] = run {
                    val blockchainRid = clusterManagement.getClusterInfo(clusterName).anchoringChain
                    val route = TopicRoute(topic, listOf())
                    IntraClusterTopicPipe(queryProvider, route, blockchainRid)
                }
            }
        }
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
