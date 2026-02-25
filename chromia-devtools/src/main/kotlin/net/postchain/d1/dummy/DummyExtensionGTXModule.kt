package net.postchain.d1.dummy

import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule

class DummyExtensionGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
) {
    override fun initializeDB(ctx: EContext) {}

    override fun getSpecialTxExtensions() = listOf(DummySpecialTxExtension())
}
