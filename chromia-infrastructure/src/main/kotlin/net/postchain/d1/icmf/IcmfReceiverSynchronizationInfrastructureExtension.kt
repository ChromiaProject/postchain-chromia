package net.postchain.d1.icmf

import net.postchain.PostchainContext
import net.postchain.client.config.FailOverConfig
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainProcess
import net.postchain.core.Shutdownable
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.d1.client.ChromiaClientProvider
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.cluster.DirectoryClusterManagement
import net.postchain.gtx.GTXModule
import net.postchain.managed.config.DappBlockchainConfiguration
import java.time.Duration

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
                val clusterManagement = createClusterManagement(configuration)
                val clientProvider = createClientProvider(clusterManagement)
                txExt.clusterManagement = clusterManagement

                val rawIcmfReceiverConfig = configuration.rawConfig["icmf"]?.get("receiver")
                    ?: throw UserMistake("Missing configuration key icmf/receiver")
                val config = IcmfReceiverBlockchainConfigData.fromGtv(rawIcmfReceiverConfig)

                if (config.global != null && config.global.topics.isNotEmpty()) {
                    val globalTopicIcmfReceiver = GlobalTopicIcmfReceiver(
                        config.global.topics.distinct().associateWith { listOf() },
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

                if (!config.blockchains.isNullOrEmpty()) {
                    val specificChainReceiver = GlobalTopicIcmfReceiver(
                        config.blockchains.groupBy { it.topic }
                            .mapValues { it.value.map { x -> BlockchainRid(x.blockchainRid) }.distinct() },
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

    open fun createClusterManagement(configuration: DappBlockchainConfiguration): ClusterManagement =
        DirectoryClusterManagement(configuration.dataSource::query)

    open fun createClientProvider(clusterManagement: ClusterManagement): ChromiaClientProvider = ChromiaClientProvider(
        failOverConfig = FailOverConfig(
            attemptsPerEndpoint = 1,
            attemptInterval = Duration.ZERO
        ), clusterManagement
    )

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
