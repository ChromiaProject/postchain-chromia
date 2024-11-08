package net.postchain.d1.icmf

import net.postchain.core.Transaction
import net.postchain.d1.RELL_SOURCE_PATH
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxOp
import java.io.File

abstract class IcmfBaseIT : ManagedModeTest() {

    fun makeTransaction(node: PostchainTestNode, chainId: Long, vararg ops: GtxOp): Transaction {
        val builder = GtxBuilder(ChainUtil.ridOf(chainId), emptyList(), cryptoSystem)
        ops.forEach { builder.addOperation(it.opName, *it.args) }
        val txData = builder
                .addNop()
                .finish().buildGtx().encode()
        return node.getBlockchainInstance(chainId).blockchainEngine.getConfiguration().getTransactionFactory()
                .decodeTransaction(txData)
    }

    private fun getIcmfConstantsCode(): Pair<String, Gtv> {
        return "lib.icmf.constants" to gtv(File(RELL_SOURCE_PATH, "lib/icmf/constants.rell").readText())
    }

    fun deployDappChain(
            signers: Set<Int> = setOf(0, 1, 2),
            configFile: String = "/net/postchain/d1/icmf/sender/blockchain_config_1.xml",
            additionRellCode: String = "",
            modifyConfig: (String) -> String = { it }
    ): Long {
        val icmfTestCode = File(RELL_SOURCE_PATH, "lib/icmf/module.rell").readText() + additionRellCode
        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource(configFile)!!.readText(),
                mapOf("lib.icmf" to gtv(icmfTestCode), getIcmfConstantsCode()))

        val dappConfigString = modifyConfig(GtvMLEncoder.encodeXMLGtv(dappGtvConfig))

        return startNewBlockchain(
                signers,
                setOf(),
                rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(dappConfigString)),
                blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())
    }
}