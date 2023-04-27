package net.postchain.d1.anchoring

import net.postchain.client.core.PostchainQuery
import net.postchain.core.BlockchainConfiguration
import net.postchain.d1.icmf.IcmfSenderGTXModule

class SystemAnchoringIcmfSenderTestGTXModule : IcmfSenderGTXModule() {
    override fun isSystemChain(configuration: BlockchainConfiguration, query: PostchainQuery): Boolean = true
}
