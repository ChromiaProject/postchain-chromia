// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.cm.cm_api.ClusterManagementImpl
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.containers.infra.MasterSyncInfra
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.Storage
import net.postchain.core.block.BlockQueries

class AnchoringDispatcher(private val storage: Storage, private val blockchainInfrastructure: BlockchainInfrastructure) {
    private val receivers = mutableMapOf<Long, AnchoringReceiver>()
    private val anchoringBlockQueries = mutableMapOf<String, BlockQueries>()
    private val localChains = mutableMapOf<Long, BlockchainRid>()
    private val subnodeChains = mutableMapOf<Long, BlockchainRid>()

    fun connectReceiver(chainID: Long, receiver: AnchoringReceiver, anchorBlockQueries: BlockQueries) {
        receivers[chainID] = receiver
        anchoringBlockQueries[receiver.cluster] = anchorBlockQueries
        localChains.filterKeys { it != chainID }.forEach { (localChainID, blockchainRid) ->
            receiver.localPipes[localChainID] = buildLocalPipe(localChainID, blockchainRid)
        }
        subnodeChains.filterKeys { it != chainID }.forEach { (subnodeChainID, blockchainRid) ->
            receiver.localPipes[subnodeChainID] = buildSubnodePipe(subnodeChainID, blockchainRid)
        }
    }

    fun connectChain(chainID: Long, blockchainRid: BlockchainRid) {
        connectChainInternal(chainID) { buildLocalPipe(chainID, blockchainRid) }
        localChains[chainID] = blockchainRid
    }

    fun connectSubnodeChain(chainID: Long, blockchainRid: BlockchainRid) {
        connectChainInternal(chainID) { buildSubnodePipe(chainID, blockchainRid) }
        subnodeChains[chainID] = blockchainRid
    }

    private fun buildLocalPipe(chainID: Long, blockchainRid: BlockchainRid): AnchoringPipe =
            AnchoringLocalPipe(chainID, blockchainRid, storage)

    private fun buildSubnodePipe(chainID: Long, blockchainRid: BlockchainRid): AnchoringPipe {
        val cm = ClusterManagementImpl { name, gtv -> anchoringBlockQueries.values.first().query(name, gtv).get() }
        val cluster = cm.getClusterOfBlockchain(blockchainRid)
        val anchoringBlockQueries = anchoringBlockQueries[cluster]!!
        return AnchoringSubnodePipe(chainID, blockchainRid, (blockchainInfrastructure as MasterSyncInfra).masterConnectionManager) { anchoringBlockQueries }
    }

    private fun connectChainInternal(chainID: Long, pipeSupplier: () -> AnchoringPipe) {
        receivers.filter { it.key != chainID && (chainID !in it.value.localPipes) }.values
                .forEach {
                    it.localPipes[chainID] = pipeSupplier()
                }
    }

    fun disconnectChain(chainID: Long) {
        receivers.remove(chainID)
        receivers.values.forEach {
            it.localPipes.remove(chainID)?.shutdown()
        }
        localChains.remove(chainID)
    }

    fun disconnectSubnodeChain(chainID: Long) {
        receivers.values.forEach {
            it.localPipes.remove(chainID)?.shutdown()
        }
        subnodeChains.remove(chainID)
    }

    fun afterCommit(chainID: Long, height: Long) {
        receivers.values.forEach {
            it.localPipes[chainID]?.newBlockAvailable(height)
        }
    }
}
