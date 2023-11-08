// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.anchoring.AnchoringProcessManagerExtension
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class D1InfrastructureFactory : ManagedEBFTInfrastructureFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure): List<BlockchainProcessManagerExtension> {
        val anchoringProcessManagerExtension = AnchoringProcessManagerExtension(postchainContext, blockchainInfrastructure)
        return listOf(anchoringProcessManagerExtension)
    }
}
