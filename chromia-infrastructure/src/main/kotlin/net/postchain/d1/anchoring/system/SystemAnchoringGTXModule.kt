package net.postchain.d1.anchoring.system

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.d1.anchoring.AnchoringPipeManager
import net.postchain.d1.anchoring.AnchoringSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * System anchoring module.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchoring" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
open class SystemAnchoringGTXModule : SimpleGTXModule<Unit>(
        Unit, mapOf(), mapOf()
) {
    private val _specialTxExtensions = listOf(
            AnchoringSpecialTxExtension { clusterManagement, _, anchoringPipeFactory ->
                AnchoringPipeManager(anchoringPipeFactory, SystemAnchoringRelevantChainsProvider(clusterManagement))
            },
            SelfReportFaultyConfigSpecialTxExtension()
    )

    override fun initializeDB(ctx: EContext) {} // Don't need anything, the "real" anchoring module creates tables etc

    override fun makeBlockBuilderExtensions() = listOf<BaseBlockBuilderExtension>()

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchoringSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}
