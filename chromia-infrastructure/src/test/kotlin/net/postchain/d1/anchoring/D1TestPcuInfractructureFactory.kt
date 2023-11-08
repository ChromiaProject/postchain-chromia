// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.devtools.mminfra.TestManagedBlockchainProcessManager
import net.postchain.devtools.mminfra.TestManagedEBFTInfrastructureFactory

// This is only used in tests, real D1 uses managed mode + containers
class D1PTestPcuInfrastructureFactory : TestManagedEBFTInfrastructureFactory() {
    override fun makeProcessManager(postchainContext: PostchainContext, blockchainInfrastructure: BlockchainInfrastructure, blockchainConfigurationProvider: BlockchainConfigurationProvider): BlockchainProcessManager {
        return TestManagedBlockchainProcessManager(postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                dataSource,
                listOf(AnchoringTestProcessManagerExtension(postchainContext, blockchainInfrastructure))
        )
    }
}
