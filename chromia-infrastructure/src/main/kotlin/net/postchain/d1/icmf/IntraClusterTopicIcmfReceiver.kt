package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.d1.query.ChromiaQueryProvider

class IntraClusterTopicIcmfReceiver(
        origins: List<Pair<String, BlockchainRid>>,
        queryProvider: ChromiaQueryProvider,
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {

    private val pipes: List<IntraClusterTopicPipe> = origins.map {
        IntraClusterTopicPipe(
                queryProvider,
                TopicRoute(it.first, listOf(it.second)),
                it.second
        )
    }

    override fun getRelevantPipes(): List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = pipes

    override fun shutdown() {
        pipes.forEach { it.shutdown() }
    }
}
