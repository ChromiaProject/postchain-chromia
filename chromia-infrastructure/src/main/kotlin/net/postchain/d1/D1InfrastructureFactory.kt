// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.anchoring.AnchoringProcessManagerExtension
import net.postchain.managed.LegacyAnchoringBlockchainProcessManagerExtension
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class D1InfrastructureFactory : ManagedEBFTInfrastructureFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext): List<BlockchainProcessManagerExtension> {
        return listOf(
                AnchoringProcessManagerExtension(postchainContext),
                LegacyAnchoringBlockchainProcessManagerExtension(postchainContext) // TODO: Temporary until new config update functionality
        )
    }
}