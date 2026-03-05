package net.postchain.d1.anchoring.cluster

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.d1.anchoring.AnchoringPipeManager
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.d1.anchoring.resourceusage.ResourceUsageStatisticsSpecialTxExtension
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * Cluster anchoring module.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchoring" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
class ClusterAnchoringGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp.OP_NAME to { conf, opData: ExtOpData -> ResourceUsageStatisticsValidateNodeSignatureDummyOp(conf, opData) }),
        mapOf()
), PostchainContextAware, MetadataProvider {

    override fun getMetadata() =
            GTXModuleMetadata(
                    operations = mapOf(
                            ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp.OP_NAME to
                                    ResourceUsageStatisticsSpecialTxExtension.ValidateNodeSignatureOp.metadata
                    ),
                    queries = mapOf())

    val resourceUsageStatisticsSpecialTxExtension = ResourceUsageStatisticsSpecialTxExtension()
    private val _specialTxExtensions = listOf(
            AnchoringSpecialTxExtension { clusterManagement, anchoringBlockchainRid, anchoringPipeFactory ->
                val blockchainCluster = clusterManagement.getClusterOfBlockchain(anchoringBlockchainRid)
                val systemAnchoringChain = clusterManagement.getSystemAnchoringChain()
                val relevantChainsProvider = ClusterAnchoringRelevantChainsProvider(
                        blockchainCluster, clusterManagement, systemAnchoringChain, anchoringBlockchainRid
                )
                AnchoringPipeManager(anchoringPipeFactory, relevantChainsProvider)
            },
            resourceUsageStatisticsSpecialTxExtension)

    override fun initializeDB(ctx: EContext) {} // Don't need anything, the "real" anchoring module creates tables etc

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
            listOf(ClusterAnchoringIcmfBlockBuilderExtension())

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchoringSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        resourceUsageStatisticsSpecialTxExtension.nodePubkey = postchainContext.appConfig.pubKeyByteArray
        resourceUsageStatisticsSpecialTxExtension.nodePrivkey = postchainContext.appConfig.privKeyByteArray
        resourceUsageStatisticsSpecialTxExtension.signers = configuration.signers
    }
}

class ResourceUsageStatisticsValidateNodeSignatureDummyOp(private val conf: Unit, private val extOpData: ExtOpData) : GTXOperation(extOpData) {
    override fun checkCorrectness() {}
    override fun apply(ctx: TxEContext): Boolean {
        return true
    }
}