package net.postchain.d1.iccf

import assertk.assertThat
import assertk.assertions.containsExactly
import net.postchain.base.BaseBlockQueries
import net.postchain.client.core.PostchainQuery
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.d1.anchoring.D1TestInfrastructureFactory
import net.postchain.d1.iccf.IccfGTXOperation.Companion.ICCF_OP_NAME
import net.postchain.d1.rell.anchoring_chain_common.getAnchoringTransactionForBlockRid
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.mminfra.MockManagedNodeDataSource
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.TimeUnit

@Timeout(60, unit = TimeUnit.SECONDS)
class IccfIT : ManagedModeTest() {

    private val hashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)
    private val sourceDappGtvConfig = GtvMLParser.parseGtvML(
            javaClass.getResource("/net/postchain/d1/iccf/blockchain_config_source_1.xml")!!.readText())
    private val targetDappGtvConfig = GtvMLParser.parseGtvML(
            javaClass.getResource("/iccf/iccf_target.xml")!!.readText())
    private val signers = setOf(0, 1, 2, 3)

    override fun createManagedNodeDataSource(): MockManagedNodeDataSource {
        return object : MockManagedNodeDataSource() {
            override fun query(name: String, args: Gtv): Gtv {
                val brid = BlockchainRid(args.asDict()["blockchain_rid"]!!.asByteArray())
                return when (name) {
                    "nm_get_blockchain_state" -> gtv(getBlockchainState(brid).name)
                    else -> super.query(name, args)
                }
            }
        }
    }

    @Test
    fun intraCluster() {
        startManagedSystem(4, 0)

        startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource("/anchoring/system_anchoring.xml")!!.readText())))
        val clusterAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource("/anchoring/cluster_anchoring.xml")!!.readText())))

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
        val txRids = targetChainBlockQueries.getBlockTransactionRids(blockRid).get()
        assertThat(txRids.map { it.toHex() }).containsExactly(iccfTx.getRID().toHex())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "/anchoring/cluster_anchoring.xml",
        "/anchoring/cluster_anchoring_batch.xml"
    ])
    fun intraNetwork(clusterAnchoringConfig: String) {
        startManagedSystem(4, 0)

        val systemAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource("/anchoring/system_anchoring.xml")!!.readText())))
        val clusterAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource(clusterAnchoringConfig)!!.readText())))

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

    @ParameterizedTest
    @ValueSource(strings = [
        "/anchoring/cluster_anchoring.xml",
        "/anchoring/cluster_anchoring_batch.xml"
    ])
    fun cacProof(clusterAnchoringConfig: String) {
        startManagedSystem(4, 0)

        val systemAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource("/anchoring/system_anchoring.xml")!!.readText())))
        val clusterAnchoringChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(GtvMLParser.parseGtvML(javaClass.getResource(clusterAnchoringConfig)!!.readText())))

        // We need some dummyu chain to produce an anchoring tx
        val dummyChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(sourceDappGtvConfig))
        startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(targetDappGtvConfig))
        // Target chain will get chain id == 5 and will be considered to be in another cluster
        val targetChain = startNewBlockchain(signers, setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(targetDappGtvConfig))
        buildBlock(dummyChain, 0)
        buildBlock(clusterAnchoringChain, 0)
        buildBlock(systemAnchoringChain, 0)

        val dummyChainBlockQueries = getChainNodes(dummyChain)[0].blockQueries(dummyChain) as BaseBlockQueries

        val dummyBlockRid = dummyChainBlockQueries.getBlockRid(0).get()!!
        val clusterAnchoringBlockQueries = getChainNodes(clusterAnchoringChain)[0].blockQueries(clusterAnchoringChain) as BaseBlockQueries
        val clusterAnchoringTx = (PostchainQuery { name, args -> clusterAnchoringBlockQueries.query(name, args).get() })
                .getAnchoringTransactionForBlockRid(ChainUtil.ridOf(dummyChain), dummyBlockRid)!!
        val clusterAnchoringProof = clusterAnchoringBlockQueries.getConfirmationProof(clusterAnchoringTx.txRid.data).get()!!

        val iccfTx = enqueueTxWithOps(targetChain, listOf(
                OpData(ICCF_OP_NAME, arrayOf(
                        gtv(ChainUtil.ridOf(clusterAnchoringChain)),
                        gtv(clusterAnchoringProof.hash),
                        gtv(
                                GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(clusterAnchoringProof)),
                        ))),
                OpData("iccf_test", arrayOf(GtvDecoder.decodeGtv(clusterAnchoringTx.txData.data), gtv(false)))
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
        val gtxBuilder = GtxBuilder(ChainUtil.ridOf(chainId), listOf(), cryptoSystem, hashCalculator)
        operations.forEach { gtxBuilder.addOperation(it.opName, *it.args) }

        val module = (nodes[0].getBlockchainInstance(chainId).blockchainEngine.getConfiguration() as GTXModuleAware).module
        val tx = GTXTransactionFactory(ChainUtil.ridOf(chainId), module, cryptoSystem, hashCalculator)
                .build(gtxBuilder.finish().buildGtx())

        nodes.forEach { it.transactionQueue(chainId).enqueue(tx) }
        return tx
    }
}