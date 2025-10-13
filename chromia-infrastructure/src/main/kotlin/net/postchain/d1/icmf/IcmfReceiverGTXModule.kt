package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchorHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.AnchoredHeaderOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageHashOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.MessageOp
import net.postchain.d1.icmf.IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.OperationWrapper
import net.postchain.gtx.SnapshotAware
import net.postchain.gtx.SnapshotContext
import net.postchain.gtx.TransactorMaker
import net.postchain.gtx.data.ExtOpData

class IcmfReceiverGTXModule : GTXModule, OperationWrapper, MetadataProvider, SnapshotAware {
    companion object : KLogging()

    private val dbOperations: IcmfReceiverDatabaseOperations = IcmfReceiverDatabaseOperationsImpl()
    private val receiverRepository: IcmfReceiverRepository = IcmfReceiverRepository(dbOperations)
    private val specialTxExtension = IcmfReceiverSpecialTxExtension(receiverRepository)
    private val _specialTxExtensions = listOf(specialTxExtension)
    private lateinit var delegateTransactorMaker: TransactorMaker
    private val blockBuilderExtension = IcmfReceiverBlockBuilderExtension()

    private val operations: Map<String, (ExtOpData) -> Transactor> = mapOf(
            AnchorHeaderOp.OP_NAME to ::DummyGTXOperation,
            AnchoredHeaderOp.OP_NAME to ::DummyGTXOperation,
            NonAnchoredHeaderOp.OP_NAME to ::DummyGTXOperation,
            MessageHashOp.OP_NAME to ::DummyGTXOperation,
            MessageOp.OP_NAME to { extOpData ->
                val delegate = delegateTransactorMaker.makeTransactor(extOpData)
                IcmfReceiveGTXOperation(extOpData, delegate)
            }
    )

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(
                    AnchorHeaderOp.OP_NAME to AnchorHeaderOp.metadata,
                    AnchoredHeaderOp.OP_NAME to AnchoredHeaderOp.metadata,
                    NonAnchoredHeaderOp.OP_NAME to NonAnchoredHeaderOp.metadata,
                    MessageHashOp.OP_NAME to MessageHashOp.metadata,
                    MessageOp.OP_NAME to MessageOp.metadata,
            ),
            queries = mapOf()
    )

    override fun makeTransactor(opData: ExtOpData) = operations[opData.opName]?.let { it(opData) }
            ?: throw UserMistake("Unknown operation: ${opData.opName}")

    override fun getOperations() = operations.keys

    override fun getQueries() = setOf<String>()

    override fun query(ctxt: EContext, name: String, args: Gtv) = throw UserMistake("Unknown query: $name")

    override fun initializeDB(ctx: EContext) {
        dbOperations.initialize(ctx)
        specialTxExtension.initialDappProvidedTopics = dbOperations.loadDappProvidedReceiverTopics(ctx)
    }

    override fun makeBlockBuilderExtensions() = listOf(blockBuilderExtension)

    fun getBlockBuilderExtension() = blockBuilderExtension

    override fun getSpecialTxExtensions() = _specialTxExtensions

    override fun getWrappingOperations() = setOf(MessageOp.OP_NAME)

    override fun injectDelegateTransactorMaker(transactorMaker: TransactorMaker) {
        this.delegateTransactorMaker = transactorMaker
    }

    override fun initializeSnapshotContext(context: SnapshotContext) {
        receiverRepository.snapshotContext = context
    }

    override fun getInitialDatums(ctx: EContext): List<SnapshotDatum> = receiverRepository.getInitialReceiverDatums(ctx)

    // We don't have any permanent datums
    override fun getPermanentDatumIdMax(ctx: EContext): Long? = null
    override fun getPermanentDatums(ctx: EContext, datumIdFrom: Long, datumHandler: (datum: SnapshotDatum?) -> Boolean) {}

    override fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>) {
        receiverRepository.persistDatums(ctx, datumList)
    }

    inner class IcmfReceiveGTXOperation(extOpData: ExtOpData, private val delegate: Transactor) : GTXOperation(extOpData) {
        private lateinit var parsedOp: MessageOp

        override fun checkCorrectness() {
            parsedOp = MessageOp.fromOpNameAndArgs(data.opName, data.args)
                    ?: throw UserMistake("Invalid arguments to ${data.opName} operation")
            try {
                delegate.checkCorrectness()
            } catch (e: UserMistake) {
                logger.error("Delegate ${MessageOp.OP_NAME} operation is not correct, blocking topic ${parsedOp.topic} for this block")
                specialTxExtension.blockPipe(parsedOp.topic)
                throw UserMistake("Delegate ${MessageOp.OP_NAME} operation is not correct", e)
            }
        }

        override fun apply(ctx: TxEContext): Boolean {
            val success = try {
                delegate.apply(ctx)
            } catch (e: UserMistake) {
                logger.warn("Delegate ${MessageOp.OP_NAME} operation failed, blocking topic ${parsedOp.topic} for this block")
                specialTxExtension.blockPipe(parsedOp.topic)
                throw UserMistake("Delegate ${MessageOp.OP_NAME} operation failed: ${e.message}", e)
            }
            if (!success) {
                logger.warn("Delegate ${MessageOp.OP_NAME} operation failed, blocking topic ${parsedOp.topic} for this block")
                specialTxExtension.blockPipe(parsedOp.topic)
                throw UserMistake("Delegate ${MessageOp.OP_NAME} operation failed")
            }
            return true
        }
    }
}

class DummyGTXOperation(extOpData: ExtOpData) : GTXOperation(extOpData) {
    override fun checkCorrectness() {}
    override fun apply(ctx: TxEContext) = true
}
