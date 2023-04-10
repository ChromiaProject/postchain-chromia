package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchorHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchoredHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageHashOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfReceiverGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(
                AnchorHeaderOp.OP_NAME to { _, _ ->
                    object : Transactor {
                        override fun isSpecial() = true
                        override fun checkCorrectness() {}
                        override fun apply(ctx: TxEContext) = true
                    }
                },
                AnchoredHeaderOp.OP_NAME to { _, _ ->
                    object : Transactor {
                        override fun isSpecial() = true
                        override fun checkCorrectness() {}
                        override fun apply(ctx: TxEContext) = true
                    }
                },
                NonAnchoredHeaderOp.OP_NAME to { _, _ ->
                    object : Transactor {
                        override fun isSpecial() = true
                        override fun checkCorrectness() {}
                        override fun apply(ctx: TxEContext) = true
                    }
                },
                MessageHashOp.OP_NAME to { _, _ ->
                    object : Transactor {
                        override fun isSpecial() = true
                        override fun checkCorrectness() {}
                        override fun apply(ctx: TxEContext) = true
                    }
                }
        ),
        mapOf()
) {
    private val dbOperations = IcmfDatabaseOperationsImpl()
    private val specialTxExtension = IcmfReceiverSpecialTxExtension(dbOperations)
    private val _specialTxExtensions = listOf(specialTxExtension)

    override fun initializeDB(ctx: EContext) {
        dbOperations.initialize(ctx)
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}
