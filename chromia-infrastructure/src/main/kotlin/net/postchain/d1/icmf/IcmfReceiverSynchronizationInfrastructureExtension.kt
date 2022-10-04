package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClientProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtx.GTXModule
import net.postchain.managed.config.DappBlockchainConfiguration

open class IcmfReceiverSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, GlobalTopicIcmfReceiver>()
    private val dbOperations = IcmfDatabaseOperationsImpl()
    private val cryptoSystem = Secp256K1CryptoSystem() // TODO inject CryptoSystem

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is DappBlockchainConfiguration) {
            getIcmfReceiverSpecialTxExtension(configuration.module)?.let { txExt ->
                // TODO get hold of BlockchainConfigurationData
                val topics = listOf<String>() // configData.rawConfig["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
                val clusterManagement = createClusterManagement(configuration)
                val receiver = GlobalTopicIcmfReceiver(topics,
                        cryptoSystem,
                        engine.storage,
                        configuration.chainID,
                        clusterManagement,
                        createClientProvider(),
                        dbOperations
                )
                receivers[configuration.chainID] = receiver
                txExt.receiver = receiver
                txExt.clusterManagement = clusterManagement
            }
        }
    }

    open fun createClientProvider(): PostchainClientProvider = ConcretePostchainClientProvider()

    open fun createClusterManagement(configuration: DappBlockchainConfiguration): ClusterManagement =
            DirectoryClusterManagement(configuration.dataSource::query)

    override fun disconnectProcess(process: BlockchainProcess) {
        receivers.remove(process.blockchainEngine.getConfiguration().chainID)?.shutdown()
    }

    override fun shutdown() {
        receivers.values.forEach { it.shutdown() }
    }

    private fun getIcmfReceiverSpecialTxExtension(module: GTXModule): IcmfReceiverSpecialTxExtension? {
        return module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is IcmfReceiverSpecialTxExtension)
        } as IcmfReceiverSpecialTxExtension?
    }
}
