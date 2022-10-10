// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid

sealed class Route

/**
 * Route messages matching specific topic.
 */
data class TopicRoute(
    val topic: String,
    /**
     * Empty list means all chains.
     */
    val chains: List<BlockchainRid>
) : Route()
