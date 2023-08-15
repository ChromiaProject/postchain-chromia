// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchoring

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Storage
import net.postchain.network.common.ConnectionManager
import net.postchain.network.mastersub.subnode.SubConnectionManager

class AnchoringDispatcher(private val storage: Storage, private val connectionManager: ConnectionManager) {
    private val receivers = mutableMapOf<Long, AnchoringReceiver>()
    private val localChains = mutableMapOf<Long, BlockchainRid>()
    private val subnodeChains = mutableMapOf<Long, BlockchainRid>()

    fun connectReceiver(chainID: Long, receiver: AnchoringReceiver) {
        receivers[chainID] = receiver
        localChains.filterKeys { it != chainID }.forEach { (currentChainID, brid) ->
            receiver.localPipes[currentChainID] = AnchoringLocalPipe(currentChainID, brid, storage)
        }
        subnodeChains.filterKeys { it != chainID }.forEach { (currentChainID, brid) ->
            receiver.localPipes[currentChainID] = AnchoringSubnodePipe(currentChainID, brid, connectionManager as SubConnectionManager)
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

    fun connectSubnodeChain(chainID: Long, brid: BlockchainRid, restApiUrl: String) {
        connectChainInternal(chainID) {
            AnchoringSubnodePipe(chainID, brid, connectionManager as SubConnectionManager)
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
            it.localPipes.remove(chainID)
        }
        localChains.remove(chainID)
    }

    fun disconnectSubnodeChain(chainID: Long) {
        receivers.values.forEach {
            it.localPipes.remove(chainID)
        }
        subnodeChains.remove(chainID)
    }

    fun afterCommit(chainID: Long, height: Long) {
        receivers.values.forEach {
            it.localPipes[chainID]?.setHighestSeenHeight(height)
        }
    }
}
