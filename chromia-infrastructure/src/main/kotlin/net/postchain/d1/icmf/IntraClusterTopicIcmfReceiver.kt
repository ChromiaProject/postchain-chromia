package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.d1.query.ChromiaQueryProvider

class IntraClusterTopicIcmfReceiver(
        origins: List<LocalIcmfOrigin>,
        queryProvider: ChromiaQueryProvider,
) : IcmfReceiver<TopicRoute, Long, IcmfPacket, BlockchainRid>, Shutdownable {

    private val pipes: List<IntraClusterTopicPipe> = origins.map {
        IntraClusterTopicPipe(
                queryProvider,
                TopicRoute(it.topic, listOf(it.blockchainRid)),
                it.blockchainRid,
                it.skipToHeight
        )
    }

    override fun getRelevantPipes(): List<IcmfPipe<TopicRoute, Long, IcmfPacket, BlockchainRid>> = pipes

    override fun shutdown() {
        pipes.forEach { it.shutdown() }
    }
}

data class LocalIcmfOrigin(
        val topic: String,
        val blockchainRid: BlockchainRid,
        val skipToHeight: Long = 0
)
