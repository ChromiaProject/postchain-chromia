// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.containers.infra.MasterBlockchainInfra
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.d1.anchoring.AnchoringProcessManagerExtension

class D1MasterInfrastructureFactory : MasterManagedEbftInfraFactory() {
    override fun makeProcessManager(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure, blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return ContainerManagedBlockchainProcessManager(postchainContext,
                blockchainInfrastructure as MasterBlockchainInfra,
                blockchainConfigurationProvider,
                listOf(AnchoringProcessManagerExtension(postchainContext))
        )
    }
}