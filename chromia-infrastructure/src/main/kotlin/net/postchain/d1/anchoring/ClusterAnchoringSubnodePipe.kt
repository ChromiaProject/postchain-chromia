// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import java.lang.Long.max
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class ClusterAnchoringSubnodePipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        restApiUrl: String
) : ClusterAnchoringPipe {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    companion object : KLogging()

    private val client = ConcretePostchainClientProvider().createClient(
            PostchainClientConfig(
                    blockchainRid = blockchainRid,
                    endpointPool = EndpointPool.singleUrl(restApiUrl),
                    failOverConfig = FailOverConfig(attemptsPerEndpoint = 1, attemptInterval = Duration.ZERO),
                    connectTimeout = Duration.ofSeconds(10),
                    responseTimeout = Duration.ofSeconds(10)
            )
    )

    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    override fun fetchNext(currentPointer: Long): ClusterAnchoringPacket? =
            try {
                client.blockAtHeightSync(currentPointer)
            } catch (e: Exception) {
                logger.warn(e) { "Block fetching from sub node failed: $e" }
                null
            }?.let {
                ClusterAnchoringPacket(
                        currentPointer,
                        it.rid,
                        it.header,
                        it.witness
                )
            }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
