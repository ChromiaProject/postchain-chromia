package net.postchain.d1.iccf

import assertk.assertThat
import assertk.assertions.containsExactly
import net.postchain.base.BaseBlockQueries
import net.postchain.client.core.PostchainQuery
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.d1.RELL_SOURCE_PATH
import net.postchain.d1.anchoring.D1TestInfrastructureFactory
import net.postchain.d1.getClusterAnchoringChainConfig
import net.postchain.d1.getSystemAnchoringChainConfig
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder.Companion.ICCF_OP_NAME
import net.postchain.d1.rell.anchoring_chain_common.getAnchoringTransactionForBlockRid
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Test
import java.io.File

class IccfIT : ManagedModeTest() {
    private val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    private val iccfRellCode = File(RELL_SOURCE_PATH, "lib/iccf/module.rell").readText()
    private val iccfRellTestCode = javaClass.getResource("/net/postchain/d1/iccf/rell/iccf_test.rell")!!.readText()
    private val sourceDappGtvConfig = GtvMLParser.parseGtvML(
            javaClass.getResource("/net/postchain/d1/iccf/blockchain_config_source_1.xml")!!.readText())
    private val targetDappGtvConfig = GtvMLParser.parseGtvML(
            javaClass.getResource("/net/postchain/d1/iccf/blockchain_config_target_1.xml")!!.readText(), mapOf(
            "lib.iccf" to gtv(iccfRellCode + iccfRellTestCode)
    ))
    private val signers = setOf(0, 1, 2, 3)

    @Test
    fun intraCluster() {
        startManagedSystem(4, 0)

        startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(getSystemAnchoringChainConfig()))
        val clusterAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(getClusterAnchoringChainConfig("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")))

        val sourceChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(sourceDappGtvConfig))
        val targetChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(targetDappGtvConfig))

        val txToProve = enqueueTxWithOps(sourceChain, listOf(
                OpData("gtx_test", arrayOf(gtv(1), gtv("iccf_test")))
        ))
        buildBlock(sourceChain, 0)
        buildBlock(clusterAnchoringChain, 0)

        val sourceChainBlockQueries = getChainNodes(sourceChain)[0].blockQueries(sourceChain) as BaseBlockQueries
        val txProof = sourceChainBlockQueries.getConfirmationProof(txToProve.getRID()).get()

        val iccfTx = enqueueTxWithOps(targetChain, listOf(
                OpData(ICCF_OP_NAME, arrayOf(gtv(ChainUtil.ridOf(sourceChain)), gtv(txToProve.getHash()), gtv(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(txProof!!))))),
                OpData("iccf_test", arrayOf(txToProve.gtvData, gtv(false)))
        ))
        buildBlock(targetChain, 0)
        val targetChainBlockQueries = getChainNodes(targetChain)[0].blockQueries(targetChain)
        val blockRid = targetChainBlockQueries.getBlockRid(0).get()!!
        assertThat(targetChainBlockQueries.getBlockTransactionRids(blockRid).get().map { it.toHex() }).containsExactly(iccfTx.getRID().toHex())
    }

    @Test
    fun intraNetwork() {
        startManagedSystem(4, 0)

        val systemAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(getSystemAnchoringChainConfig()))
        val clusterAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(getClusterAnchoringChainConfig("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")))

        val sourceChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(sourceDappGtvConfig))
        startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(targetDappGtvConfig))
        // Target chain will get chain id == 5 and will be considered to be in another cluster
        val targetChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(targetDappGtvConfig))

        val txToProve = enqueueTxWithOps(sourceChain, listOf(
                OpData("gtx_test", arrayOf(gtv(1), gtv("iccf_test")))
        ))
        buildBlock(sourceChain, 0)
        buildBlock(clusterAnchoringChain, 0)
        buildBlock(systemAnchoringChain, 0)

        val sourceChainBlockQueries = getChainNodes(sourceChain)[0].blockQueries(sourceChain) as BaseBlockQueries
        val txProof = sourceChainBlockQueries.getConfirmationProof(txToProve.getRID()).get()!!

        val sourceBlockRid = GtvDecoder.decodeGtv(txProof.blockHeader).merkleHash(hashCalculator)
        val clusterAnchoringBlockQueries = getChainNodes(clusterAnchoringChain)[0].blockQueries(clusterAnchoringChain) as BaseBlockQueries
        val clusterAnchoringTx = (PostchainQuery { name, args -> clusterAnchoringBlockQueries.query(name, args).get() })
                .getAnchoringTransactionForBlockRid(ChainUtil.ridOf(sourceChain), sourceBlockRid)!!
        val clusterAnchoringProof = clusterAnchoringBlockQueries.getConfirmationProof(clusterAnchoringTx.txRid.data).get()

        val iccfTx = enqueueTxWithOps(targetChain, listOf(
                OpData(ICCF_OP_NAME, arrayOf(
                        gtv(ChainUtil.ridOf(sourceChain)),
                        gtv(txToProve.getHash()),
                        gtv(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(txProof))),
                        gtv(clusterAnchoringTx.txData),
                        gtv(clusterAnchoringTx.txOpIndex),
                        gtv(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(clusterAnchoringProof!!)))
                )),
                OpData("iccf_test", arrayOf(txToProve.gtvData, gtv(true)))
        ))
        buildBlock(targetChain, 0)
        val targetChainBlockQueries = getChainNodes(targetChain)[0].blockQueries(targetChain)
        val blockRid = targetChainBlockQueries.getBlockRid(0).get()!!
        assertThat(targetChainBlockQueries.getBlockTransactionRids(blockRid).get().map { it.toHex() }).containsExactly(iccfTx.getRID().toHex())
    }

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", D1TestInfrastructureFactory::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("clusterManagementMock", IccfTestClusterManagement::class.qualifiedName)
    }

    private fun enqueueTxWithOps(chainId: Long, operations: List<OpData>): GTXTransaction {
        val nodes = getChainNodes(chainId)
        val gtxBuilder = GtxBuilder(ChainUtil.ridOf(chainId), listOf(), cryptoSystem)
        operations.forEach { gtxBuilder.addOperation(it.opName, *it.args) }

        val module = (nodes[0].getBlockchainInstance(chainId).blockchainEngine.getConfiguration() as GTXModuleAware).module
        val tx = GTXTransactionFactory(ChainUtil.ridOf(chainId), module, cryptoSystem)
                .build(gtxBuilder.finish().buildGtx())

        nodes.forEach { it.transactionQueue(chainId).enqueue(tx) }
        return tx
    }
}