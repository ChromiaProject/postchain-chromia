package net.postchain.d1.icmf

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainContext
import net.postchain.crypto.CryptoSystem
import net.postchain.core.ExecutionContext
import net.postchain.crypto.SigMaker
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class IcmfTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any,
                                             partialContext: BlockchainContext,
                                             blockSigMaker: SigMaker,
                                             eContext: ExecutionContext,
                                             cryptoSystem: CryptoSystem): GTXBlockchainConfiguration {
        return TestBlockchainConfiguration(
                configurationData as BlockchainConfigurationData,
                cryptoSystem,
                partialContext,
                blockSigMaker,
                createGtxModule(partialContext.blockchainRID, configurationData, eContext)
        )
    }
}
