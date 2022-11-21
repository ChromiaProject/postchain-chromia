// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.anchoring.AnchoringProcessManagerExtension
import net.postchain.managed.LegacyAnchoringBlockchainProcessManagerExtension

class D1MasterInfrastructureFactory : MasterManagedEbftInfraFactory() {
    override fun getProcessManagerExtensions(postchainContext: PostchainContext): List<BlockchainProcessManagerExtension> {
        return listOf(
                AnchoringProcessManagerExtension(postchainContext),
                LegacyAnchoringBlockchainProcessManagerExtension(postchainContext) // TODO: Temporary until new config update functionality
        )
    }
}