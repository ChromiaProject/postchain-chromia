// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.containers.infra.MasterSyncInfra
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.Storage
import net.postchain.core.block.BlockQueries

class AnchoringDispatcher(private val storage: Storage, private val blockchainInfrastructure: BlockchainInfrastructure) {
    private val receivers = mutableMapOf<Long, AnchoringReceiver>()
    private val localChains = mutableMapOf<Long, BlockchainRid>()
    private val subnodeChains = mutableMapOf<Long, BlockchainRid>()

    private lateinit var anchorBlockQueries: BlockQueries

    fun connectReceiver(chainID: Long, receiver: AnchoringReceiver, anchorBlockQueries: BlockQueries) {
        receivers[chainID] = receiver
        this.anchorBlockQueries = anchorBlockQueries
        localChains.filterKeys { it != chainID }.forEach { (currentChainID, brid) ->
            receiver.localPipes[currentChainID] = AnchoringLocalPipe(currentChainID, brid, storage)
        }
        subnodeChains.filterKeys { it != chainID }.forEach { (currentChainID, brid) ->
            receiver.localPipes[currentChainID] =
                    AnchoringSubnodePipe(currentChainID, brid, (blockchainInfrastructure as MasterSyncInfra).masterConnectionManager) { anchorBlockQueries }
        }
    }

    fun connectChain(chainID: Long) {
        val brid = withReadConnection(storage, chainID) {
            DatabaseAccess.of(it).getBlockchainRid(it)!!
        }

        connectChainInternal(chainID) {
            AnchoringLocalPipe(chainID, brid, storage)
        }

        localChains[chainID] = brid
    }

    fun connectSubnodeChain(chainID: Long, brid: BlockchainRid) {
        connectChainInternal(chainID) {
            AnchoringSubnodePipe(chainID, brid, (blockchainInfrastructure as MasterSyncInfra).masterConnectionManager) { anchorBlockQueries }
        }

        subnodeChains[chainID] = brid
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
