package net.postchain.d1.icmf

import net.postchain.client.core.PostchainQuery
import net.postchain.core.BlockchainConfiguration

class IcmfSenderTestGTXModule : IcmfSenderGTXModule() {
    override fun isSystemChain(configuration: BlockchainConfiguration, query: PostchainQuery): Boolean = false
}