// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import mu.KLogging
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import java.time.Duration

class ClusterAnchoringSubnodePipe(
        override val chainID: Long,
        override val blockchainRid: BlockchainRid,
        restApiUrl: String
) : ClusterAnchoringPipe {

    companion object : KLogging()

    private val client = ConcretePostchainClientProvider().createClient(
            PostchainClientConfig(
                    blockchainRid = blockchainRid,
                    endpointPool = EndpointPool.singleUrl(restApiUrl),
                    failOverConfig = FailOverConfig(attemptsPerEndpoint = 1, attemptInterval = Duration.ZERO)
            )
    )

    override fun setHighestSeenHeight(height: Long) {}
    override fun mightHaveNewPackets() = true

    override fun fetchNext(currentPointer: Long): ClusterAnchoringPacket? =
            try {
                // TODO set timeout
                client.blockAtHeightSync(currentPointer)
            } catch (e: Exception) {
                logger.warn("Block fetching from sub node failed")
                null
            }?.let {
                ClusterAnchoringPacket(
                        currentPointer,
                        it.rid,
                        it.header,
                        it.witness
                )
            }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {}
}
