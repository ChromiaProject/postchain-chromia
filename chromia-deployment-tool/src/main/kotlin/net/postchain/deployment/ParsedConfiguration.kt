package net.postchain.deployment

import net.postchain.common.BlockchainRid

data class ParsedConfiguration(
        val runXml: String,
        val blockchainRid: BlockchainRid?,
        val containerName: String?
)
