// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.impl.PostchainClientProviderImpl
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import java.lang.Long.max
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class AnchoringSubnodePipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        restApiUrl: String
) : AnchoringPipe {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    companion object : KLogging()

    private val client = PostchainClientProviderImpl().createClient(
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

    override fun numberOfNewPackets() = highestSeen.get() - lastCommitted.get()

    override fun fetchNext(currentPointer: Long): AnchoringPacket? =
            try {
                client.blockAtHeight(currentPointer)
            } catch (e: Exception) {
                logger.warn(e) { "Block fetching from sub node failed: $e" }
                null
            }?.let {
                AnchoringPacket(
                        currentPointer,
                        it.rid.data,
                        it.header.data,
                        it.witness.data
                )
            }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
