package net.postchain.d1.icmf

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.ExecutionContext
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class IcmfTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: ExecutionContext): GTXBlockchainConfiguration {
        return TestBlockchainConfiguration(
                configurationData as BlockchainConfigurationData,
                createGtxModule(configurationData.context.blockchainRID, configurationData, eContext)
        )
    }
}
