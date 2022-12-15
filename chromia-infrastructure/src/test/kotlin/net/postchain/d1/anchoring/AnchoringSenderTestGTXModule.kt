package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule

class AnchoringSenderTestGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(AnchoringTestBBExtension())
    }

    override fun initializeDB(ctx: EContext) {
    }
}