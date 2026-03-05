package net.postchain.hybridcompute

import net.postchain.PostchainContext
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension

/**
 * Dummy, not needed any longer.
 */
@Suppress("unused")
class HybridComputeSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) :
        SynchronizationInfrastructureExtension {

    override fun connectProcess(process: BlockchainProcess) {}

    override fun disconnectProcess(process: BlockchainProcess) {}

    override fun shutdown() {}
}
