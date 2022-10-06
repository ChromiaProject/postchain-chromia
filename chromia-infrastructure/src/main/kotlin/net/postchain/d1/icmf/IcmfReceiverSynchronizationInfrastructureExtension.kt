package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.PostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtx.GTXModule
import net.postchain.managed.config.DappBlockchainConfiguration

open class IcmfReceiverSynchronizationInfrastructureExtension(postchainContext: PostchainContext) :
    SynchronizationInfrastructureExtension {
    private val receivers = mutableMapOf<Long, MutableList<Shutdownable>>()
    private val dbOperations = IcmfDatabaseOperationsImpl()
    private val cryptoSystem = postchainContext.cryptoSystem

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val configuration = engine.getConfiguration()
        if (configuration is DappBlockchainConfiguration) {
            getIcmfReceiverSpecialTxExtension(configuration.module)?.let { txExt ->
                val clientProvider = createClientProvider()
                val clusterManagement = createClusterManagement(configuration)
                txExt.clusterManagement = clusterManagement

                val topics = configuration.rawConfig["icmf"]!!["receiver"]!!["global"]!!["topics"]!!.asArray()
                    .map { it.asString() }.distinct()
                if (topics.isNotEmpty()) {
                    val globalTopicIcmfReceiver = GlobalTopicIcmfReceiver(
                        topics.associateWith { listOf() },
                        cryptoSystem,
                        engine.storage,
                        configuration.chainID,
                        clusterManagement,
                        clientProvider,
                        dbOperations
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(globalTopicIcmfReceiver)
                    txExt.receivers.add(globalTopicIcmfReceiver)
                }

                val blockchains = configuration.rawConfig["icmf"]!!["receiver"]!!["blockchain"]!!.asArray()
                    .map { BlockchainRid(it["bc-rid"]!!.asByteArray()) to it["topic"]!!.asString() }
                if (blockchains.isNotEmpty()) {
                    val specificChainReceiver = GlobalTopicIcmfReceiver(
                        blockchains.groupBy { it.second }.mapValues { it.value.map { x -> x.first }.distinct() },
                        cryptoSystem,
                        engine.storage,
                        configuration.chainID,
                        clusterManagement,
                        clientProvider,
                        dbOperations
                    )
                    receivers.computeIfAbsent(configuration.chainID) { mutableListOf() }.add(specificChainReceiver)
                    txExt.receivers.add(specificChainReceiver)
                }
            }
        }
    }

    open fun createClientProvider(): PostchainClientProvider = ConcretePostchainClientProvider()

    open fun createClusterManagement(configuration: DappBlockchainConfiguration): ClusterManagement =
        DirectoryClusterManagement(configuration.dataSource::query)

    override fun disconnectProcess(process: BlockchainProcess) {
        receivers.remove(process.blockchainEngine.getConfiguration().chainID)?.forEach { it.shutdown() }
    }

    override fun shutdown() {
        receivers.values.forEach { chain -> chain.forEach { it.shutdown() } }
    }

    private fun getIcmfReceiverSpecialTxExtension(module: GTXModule): IcmfReceiverSpecialTxExtension? {
        return module.getSpecialTxExtensions().firstOrNull { ext ->
            (ext is IcmfReceiverSpecialTxExtension)
        } as IcmfReceiverSpecialTxExtension?
    }
}
