package net.postchain.d1.icmf

import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.core.Storage
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.query.ChromiaQueryProvider

class LocalTopicIcmfReceiver(
        origins: List<LocalIcmfOrigin>,
        private val queryProvider: ChromiaQueryProvider,
        private val clusterManagement: ClusterManagement,
        private val clientProvider: ChromiaClientProvider,
        private val myChainId: Long,
        private val myBlockchainRid: BlockchainRid,
        private val storage: Storage,
        private val dbOperations: IcmfDatabaseOperations
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {

    private val pipes: List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = run {
        val myCluster = clusterManagement.getClusterOfBlockchain(myBlockchainRid)
        origins.map {
            val senderCluster = clusterManagement.getClusterOfBlockchain(it.blockchainRid)
            if (senderCluster == myCluster) {
                IntraClusterTopicPipe(
                        queryProvider,
                        TopicRoute(it.topic, listOf(it.blockchainRid)),
                        it.blockchainRid,
                        it.skipToHeight
                )
            } else {
                val lastMessageHeight = withReadConnection(storage, myChainId) { ctx ->
                    dbOperations.loadLastMessageHeight(ctx, it.blockchainRid, it.topic)
                }

                InterClusterTopicPipe(
                        TopicRoute(it.topic, listOf(it.blockchainRid)),
                        it.blockchainRid,
                        clientProvider,
                        it.skipToHeight,
                        lastMessageHeight
                )
            }
        }
    }

    override fun getRelevantPipes(): List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = pipes

    override fun shutdown() {
        pipes.forEach { it.shutdown() }
    }
}
