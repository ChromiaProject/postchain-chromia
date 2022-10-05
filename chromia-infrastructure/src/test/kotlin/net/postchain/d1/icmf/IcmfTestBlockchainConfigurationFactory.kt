package net.postchain.d1.icmf

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory

open class IcmfTestBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {

    override fun makeBlockchainConfiguration(
        configurationData: Any,
        eContext: EContext,
        cryptoSystem: CryptoSystem
    ): GTXBlockchainConfiguration {
        val configData = configurationData as BlockchainConfigurationData
        return TestBlockchainConfiguration(
            configData,
            cryptoSystem,
            createGtxModule(configurationData.context.blockchainRID, configData, eContext)
        )
    }
}
