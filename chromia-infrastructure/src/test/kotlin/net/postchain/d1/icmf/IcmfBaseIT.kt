package net.postchain.d1.icmf

import net.postchain.core.Transaction
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxOp

abstract class IcmfBaseIT : ManagedModeTest() {

    fun makeTransaction(node: PostchainTestNode, chainId: Long, vararg ops: GtxOp): Transaction {
        val builder = GtxBuilder(ChainUtil.ridOf(chainId), emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
        ops.forEach { builder.addOperation(it.opName, *it.args) }
        val txData = builder
                .addNop()
                .finish().buildGtx().encode()
        return node.getBlockchainInstance(chainId).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(txData)
    }

    fun deployDappChain(
            signers: Set<Int> = setOf(0, 1, 2),
            configFile: String = "/icmf/sender.xml"
    ): Long {
        val dappGtvConfig = GtvMLParser.parseGtvML(javaClass.getResource(configFile)!!.readText())

        return startNewBlockchain(
                signers,
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig),
                blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())
    }
}