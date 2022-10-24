package net.postchain.deployment

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

data class BlockchainConfigurations(
    val blockchainRid: BlockchainRid,
    val name: String,
    val configurations: List<Pair<Long, Gtv>>
)
