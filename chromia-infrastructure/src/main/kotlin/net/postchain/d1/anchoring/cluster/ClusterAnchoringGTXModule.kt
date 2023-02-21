package net.postchain.d1.anchoring.cluster

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * Cluster anchoring module.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchoring" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
class ClusterAnchoringGTXModule : SimpleGTXModule<Unit>(
        Unit, mapOf(), mapOf()
) {
    private val _specialTxExtensions = listOf(AnchoringSpecialTxExtension { clusterManagement, anchoringBlockchainRid ->
        val blockchainCluster = clusterManagement.getClusterOfBlockchain(anchoringBlockchainRid)
        val systemAnchoringChain = clusterManagement.getSystemAnchoringChain()
        ClusterAnchoringReceiver(blockchainCluster, systemAnchoringChain, clusterManagement)
    })

    override fun initializeDB(ctx: EContext) {} // Don't need anything, the "real" anchoring module creates tables etc

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
            listOf(ClusterAnchoringIcmfBlockBuilderExtension())

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchoringSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}
