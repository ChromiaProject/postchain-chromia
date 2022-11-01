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
import net.postchain.d1.query.ChromiaQueryProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.minutes

class GlobalTopicIcmfReceiver(
    topics: Map<String, List<BlockchainRid>>,
    private val cryptoSystem: CryptoSystem,
    private val storage: Storage,
    private val queryProvider: ChromiaQueryProvider,
    private val myChainId: Long,
    myBlockchainRid: BlockchainRid,
    private val clusterManagement: ClusterManagement,
    private val clientProvider: ChromiaClientProvider,
    private val dbOperations: IcmfDatabaseOperations
) : IcmfReceiver<TopicRoute, Long, String>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 1.minutes
    }

    private val routes = topics.map { TopicRoute(it.key, it.value) }
    private val pipes: ConcurrentMap<Pair<String, TopicRoute>, IcmfPipe<TopicRoute, Long, String>> = ConcurrentHashMap()
    private val jobSynchronizer = Object()
    private var job: Job? = null

    private val myCluster = clusterManagement.getClusterOfBlockchain(myBlockchainRid)

    private fun start(): Job {
        val lastMessageHeights = withReadConnection(storage, myChainId) {
            dbOperations.loadAllLastMessageHeights(it)
        }

        val allClusters = clusterManagement.getClusterNames()
        for (route in routes) {
            if (route.chains.isNotEmpty()) {
                route.chains.map { clusterManagement.getClusterOfBlockchain(it) }.distinct().forEach { clusterName ->
                    pipes[clusterName to route] = createPipe(
                        clusterName,
                        route,
                        lastMessageHeights.filter { it.topic == route.topic }.map { it.sender to it.height })
                }
            } else {
                for (clusterName in allClusters) {
                    pipes[clusterName to route] = createPipe(
                        clusterName,
                        route,
                        lastMessageHeights.filter { it.topic == route.topic }.map { it.sender to it.height })
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

    private fun createPipe(
        clusterName: String,
        route: TopicRoute,
        lastMessageHeights: List<Pair<BlockchainRid, Long>>
    ): IcmfPipe<TopicRoute, Long, String> {
        return if (clusterName == myCluster) {
            LocalTopicPipe(
                queryProvider,
                route,
                clusterName,
                cryptoSystem,
                clusterManagement
            )
        } else {
            val lastAnchorHeight = withReadConnection(storage, myChainId) {
                dbOperations.loadLastAnchoredHeight(it, clusterName, route.topic)
            }

            ClusterGlobalTopicPipe(
                route, clusterName, cryptoSystem, lastAnchorHeight, clientProvider,
                clusterManagement, lastMessageHeights
            )
        }
    }

    private fun updateClusters() {
        val currentClusters = pipes.keys.map { it.first }.toSet()
        val updatedClusters = clusterManagement.getClusterNames().toSet()
        val removedClusters = currentClusters - updatedClusters
        val addedClusters = updatedClusters - currentClusters
        for (clusterName in removedClusters) {
            for (route in routes) {
                pipes.remove(clusterName to route)?.shutdown()
            }
        }
        for (clusterName in addedClusters) {
            for (route in routes) {
                pipes[clusterName to route] = createPipe(clusterName, route, listOf())
            }
        }
    }

    override fun getRelevantPipes(): List<IcmfPipe<TopicRoute, Long, String>> {
        synchronized(jobSynchronizer) {
            if (job == null) {
                job = start()
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
