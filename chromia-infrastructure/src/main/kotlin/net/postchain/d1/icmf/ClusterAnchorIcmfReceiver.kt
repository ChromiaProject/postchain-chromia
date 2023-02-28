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
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.core.Storage
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class ClusterAnchorIcmfReceiver(
        private val topics: List<String>,
        private val cryptoSystem: CryptoSystem,
        private val storage: Storage,
        private val myChainId: Long,
        private val clusterManagement: ClusterManagement,
        private val clientProvider: ChromiaClientProvider,
        private val dbOperations: IcmfDatabaseOperations
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val pipes: ConcurrentMap<Pair<String, String>, IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = ConcurrentHashMap()
    private val jobSynchronizer = Object()
    private var job: Job? = null

    private fun start(): Job {
        val lastMessageHeights = withReadConnection(storage, myChainId) {
            dbOperations.loadAllLastMessageHeights(it)
        }

        val allClusters = clusterManagement.getClusterNames()
        for (topic in topics) {
            for (clusterName in allClusters) {
                pipes[clusterName to topic] = run {
                    val blockchainRid = clusterManagement.getClusterInfo(clusterName).anchoringChain
                    val route = TopicRoute(topic, listOf())
                    val lastMessageHeight = lastMessageHeights.firstOrNull { it.topic == topic && it.sender == blockchainRid }?.height
                            ?: -1
                    InterClusterNonAnchoredTopicPipe(route, blockchainRid, cryptoSystem, clusterName, clientProvider, clusterManagement, lastMessageHeight)
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
        val currentClusters = pipes.keys.map { it.first }.toSet()
        val updatedClusters = clusterManagement.getClusterNames().toSet()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            for (topic in topics) {
                pipes.remove(clusterName to topic)?.shutdown()
            }
        }
        for (clusterName in addedClusters) {
            for (topic in topics) {
                pipes[clusterName to topic] = run {
                    val blockchainRid = clusterManagement.getClusterInfo(clusterName).anchoringChain
                    val route = TopicRoute(topic, listOf())
                    InterClusterNonAnchoredTopicPipe(route, blockchainRid, cryptoSystem, clusterName, clientProvider, clusterManagement, -1)
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
        return pipes.values.toList()
    }

    override fun shutdown() {
        synchronized(jobSynchronizer) {
            job?.cancel()
        }
        for (pipe in pipes.values) {
            pipe.shutdown()
        }
    }
}
