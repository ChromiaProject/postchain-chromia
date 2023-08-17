// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.anchoring.AnchoringProcessManagerExtension
import net.postchain.managed.LegacyAnchoringBlockchainProcessManagerExtension

class D1MasterInfrastructureFactory : MasterManagedEbftInfraFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure): List<BlockchainProcessManagerExtension> {
        val anchoringProcessManagerExtension = AnchoringProcessManagerExtension(postchainContext, blockchainInfrastructure)
        return if (postchainContext.appConfig.isPcuEnabled()) {
            listOf(anchoringProcessManagerExtension)
        } else {
            listOf(
                    anchoringProcessManagerExtension,
                    LegacyAnchoringBlockchainProcessManagerExtension(postchainContext) // TODO: Temporary until new config update functionality
            )
        }
    }
}