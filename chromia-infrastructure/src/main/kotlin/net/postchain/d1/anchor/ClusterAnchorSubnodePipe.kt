// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.core.BlockEContext
import net.postchain.core.RemoteBlockchainProcess
import java.lang.Long.max
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class ClusterAnchorSubnodePipe(
        val process: RemoteBlockchainProcess
) : ClusterAnchorPipe {

    override val chainId = process.chainId
    override val blockchainRid = process.blockchainRid
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)
    private val client = ConcretePostchainClientProvider().createClient(
            PostchainClientConfig(
                    blockchainRid = blockchainRid,
                    endpointPool = EndpointPool.singleUrl(process.restApiUrl),
                    failOverConfig = FailOverConfig(attemptsPerEndpoint = 1, attemptInterval = Duration.ZERO)
            )
    )

    override fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    override fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    // TODO: [POS-358]: Make it async
    override fun fetchNext(currentPointer: Long): ClusterAnchorPacket? {
        val block = client.blockAtHeightSync(currentPointer + 1)

        return if (!block.isNull()) {
            highestSeen.getAndUpdate { max(it, currentPointer + 1) }

            ClusterAnchorPacket(
                    currentPointer, // TODO: [POS-358]: +1 ?
                    block["rid"]!!.asByteArray(),
                    block["header"]!!.asByteArray(),
                    block["witness"]!!.asByteArray()
            )

        } else {
            null
        }
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }
}
