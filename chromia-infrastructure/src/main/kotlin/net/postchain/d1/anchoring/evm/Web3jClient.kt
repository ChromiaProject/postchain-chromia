package net.postchain.d1.anchoring.evm

import net.postchain.client.impl.PostchainClientImpl.Companion.logger
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.eif.contracts.Anchoring
import net.postchain.eif.web3j.Web3jServiceFactory.buildServices
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.RemoteFunctionCall
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.gas.DefaultGasProvider

class Web3jClient(
        rpcUrls: List<String>,
        blockchainRid: BlockchainRid
) {

    private val web3jClients: List<Web3j> = buildServices(rpcUrls, 10_000, 10_000, 10_000, blockchainRid)
    private val anchoringMap = mutableMapOf<String, List<Anchoring>>()

    fun <T> withAnchoring(anchoringContractAddresses: String, call: (Anchoring) -> RemoteFunctionCall<T>): T {
        for (anchoring in getAnchoring(anchoringContractAddresses)) {
            try {
                val functionCall = call(anchoring)
                return functionCall.send()
            } catch (e: Exception) {
                logger.warn { "Failed to call rpc endpoint for EVM network : ${e.message}" }
            }
        }
        throw ProgrammerMistake("Failed to call all rpc endpoints for EVM network")
    }

    fun close() {
        web3jClients.forEach(Web3j::shutdown)
    }

    private fun getAnchoring(anchoringContractAddress: String): List<Anchoring> {
        return anchoringMap.getOrPut(anchoringContractAddress) {
            web3jClients.map {
                val transactionManager = ReadonlyTransactionManager(it, anchoringContractAddress)
                Anchoring.load(anchoringContractAddress, it, transactionManager, DefaultGasProvider())
            }
        }
    }
}
