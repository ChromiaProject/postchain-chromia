package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.getMerkleHashVersion
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.Gtx
import net.postchain.gtx.SnapshotContext
import kotlin.collections.forEach

class IcmfReceiverRepository(
        private val dbOperations: IcmfReceiverDatabaseOperations,
        var snapshotContext: SnapshotContext? = null
) {
    private var hasNewanchoringHeights = false
    private var hasNewMessageHeights = false
    private var hasNewSpilledMessages = false

    companion object {
        const val ANCHORING_HEIGHT_DATUM_ID = 0L
        const val MESSAGE_HEIGHT_DATUM_ID = 1L
        const val DAPP_PROVIDED_TOPICS_DATUM_ID = 2L
        const val SPILLED_MESSAGES_DATUM_ID = 3L
    }

    fun persistDatums(ctx: EContext, datumList: List<SnapshotDatum>) {
        for (datum in datumList) {
            when (datum.id) {
                ANCHORING_HEIGHT_DATUM_ID -> {
                    dbOperations.saveLastAnchoredHeights(ctx, mapAnchoringDatum(datum.data))
                }

                MESSAGE_HEIGHT_DATUM_ID -> {
                    dbOperations.saveLastMessageHeights(ctx, mapMessageHeights(datum.data))
                }

                SPILLED_MESSAGES_DATUM_ID -> {
                    processSpilledMessagesDatum(ctx, datum.data)
                }

                DAPP_PROVIDED_TOPICS_DATUM_ID -> {
                    dbOperations.saveDappProvidedReceiverTopics(ctx, mapDappProvidedTopics(datum.data))
                }

                else -> throw ProgrammerMistake("Unknown datum id ${datum.id}")
            }
        }
    }

    fun resetDatumState() {
        hasNewanchoringHeights = false
        hasNewMessageHeights = false
        hasNewSpilledMessages = false
    }

    fun emitIcmfStateDatums(bctx: BlockEContext) {
        if (snapshotContext == null) return

        if (hasNewanchoringHeights) {
            emitDatum(bctx, getAnchoringHeightDatum(bctx))
        }

        if (hasNewMessageHeights) {
            emitDatum(bctx, getMessageHeightDatum(bctx))
        }

        if (hasNewSpilledMessages) {
            emitDatum(bctx, getSpilledMessagesDatum(bctx))
        }
    }

    private fun emitDatum(bctx: BlockEContext, datum: SnapshotDatum) {
        snapshotContext?.emitDatum(bctx, datum.id, datum.data, datum.isPermanent)
    }

    fun getInitialReceiverDatums(ctx: EContext) = listOf(
            getAnchoringHeightDatum(ctx),
            getMessageHeightDatum(ctx),
            getSpilledMessagesDatum(ctx),
            getDappProviderTopicsDatum(ctx)
    )

    private fun getAnchoringHeightDatum(ctx: EContext) = SnapshotDatum(
            ANCHORING_HEIGHT_DATUM_ID,
            gtv(dbOperations.loadLastAnchoredHeights(ctx).map { it.toGtv() }),
            false
    )

    private fun getMessageHeightDatum(ctx: EContext) = SnapshotDatum(
            MESSAGE_HEIGHT_DATUM_ID,
            gtv(dbOperations.loadAllLastMessageHeights(ctx).map { it.toGtv() }),
            false
    )

    private fun getSpilledMessagesDatum(ctx: EContext) = SnapshotDatum(
            SPILLED_MESSAGES_DATUM_ID,
            gtv(dbOperations.loadSpilledMessageStates(ctx).map { it.toGtv() }),
            false
    )

    private fun getDappProviderTopicsDatum(ctx: EContext) = SnapshotDatum(
            DAPP_PROVIDED_TOPICS_DATUM_ID,
            gtv(dbOperations.loadDappProvidedReceiverTopics(ctx).map { GtvObjectMapper.toGtvDictionary(it) }),
            false
    )

    /**
     * @return The complete list of dapp provider topics
     */
    fun handleDappProviderTopicsUpdate(bctx: BlockEContext, update: IcmfReceiverEventMessage): List<IcmfReceiverEventTopic> {
        if (update.replace) {
            dbOperations.deleteDappProvidedReceiverTopics(bctx)
        }
        if (!update.topics.isNullOrEmpty()) {
            dbOperations.saveDappProvidedReceiverTopics(bctx, update.topics)
        }

        val allTopics = dbOperations.loadDappProvidedReceiverTopics(bctx)
        snapshotContext?.emitDatum(
                bctx,
                DAPP_PROVIDED_TOPICS_DATUM_ID,
                gtv(allTopics.map { GtvObjectMapper.toGtvDictionary(it) }),
                false
        )

        return allTopics
    }

    private fun mapDappProvidedTopics(data: Gtv): List<IcmfReceiverEventTopic> {
        val dataArray = data as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
        return dataArray.array.map {
            // This one already had a non-compact dict representation, let's keep it, this will be tiny anyway
            it.toObject()
        }
    }

    private fun mapMessageHeights(data: Gtv): List<MessageHeightForSender> {
        val dataArray = data as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
        return dataArray.array.map {
            val elementArray = it as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
            if (elementArray.getSize() != 3) throw ProgrammerMistake("Invalid data. Must contain 3 elements.")

            MessageHeightForSender(
                    BlockchainRid(elementArray[0].asByteArray()),
                    elementArray[1].asString(),
                    elementArray[2].asInteger()
            )
        }
    }

    private fun mapAnchoringDatum(data: Gtv): List<AnchorHeight> {
        val dataArray = data as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
        return dataArray.array.map {
            val elementArray = it as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
            if (elementArray.getSize() != 3) throw ProgrammerMistake("Invalid data. Must contain 3 elements.")

            AnchorHeight(
                    elementArray[0].asString(),
                    elementArray[1].asString(),
                    elementArray[2].asInteger()
            )
        }
    }

    private fun processSpilledMessagesDatum(ctx: EContext, data: Gtv) {
        val spilledMessageStates = mapSpilledMessagesDatum(data)

        val restoredSpilledMessages = mutableListOf<SpilledMessageWithSenderAndTopic>()

        spilledMessageStates.groupBy { it.spillHeight }.forEach { (spillHeight, state) ->
            val spillBySenderAndTopic = state.associateBy { it.sender to it.topic }

            val db = DatabaseAccess.of(ctx)
            val spillBlockRid = db.getBlockRID(ctx, spillHeight)
                    ?: throw ProgrammerMistake("Spill block $spillHeight not found")
            val rawBeginSpecialTx = db.getBlockTransactions(ctx, spillBlockRid, false)
                    .firstOrNull()?.data
                    ?: throw ProgrammerMistake("No transactions found in spill block $spillHeight")

            val beginSpecialTx = Gtx.decode(rawBeginSpecialTx)

            var cluster = ""
            var hasSeenOldestSpillMessage = false
            var anchorHeight = 0L
            var merkleHashVersion = 1L
            for (op in beginSpecialTx.gtxBody.operations) {
                when (op.opName) {
                    IcmfReceiverSpecialTxExtension.AnchorHeaderOp.OP_NAME -> {
                        val anchorHeaderOp = IcmfReceiverSpecialTxExtension.AnchorHeaderOp.fromOpData(op.asOpData())
                                ?: throw ProgrammerMistake("Invalid anchor header operation")
                        cluster = anchorHeaderOp.cluster
                        val decodedHeader = BlockHeaderData.fromBinary(anchorHeaderOp.rawHeader)
                        anchorHeight = decodedHeader.getHeight()
                    }

                    IcmfReceiverSpecialTxExtension.AnchoredHeaderOp.OP_NAME -> {
                        val anchoredHeaderOp = IcmfReceiverSpecialTxExtension.AnchoredHeaderOp.fromOpData(op.asOpData())
                                ?: throw ProgrammerMistake("Invalid anchored header operation")
                        hasSeenOldestSpillMessage = false
                        val decodedHeader = BlockHeaderData.fromBinary(anchoredHeaderOp.rawHeader)
                        merkleHashVersion = decodedHeader.getMerkleHashVersion()
                    }

                    IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp.OP_NAME -> {
                        val nonAnchoredHeaderOp = IcmfReceiverSpecialTxExtension.NonAnchoredHeaderOp.fromOpData(op.asOpData())
                                ?: throw ProgrammerMistake("Invalid non-anchored header operation")
                        cluster = ""
                        hasSeenOldestSpillMessage = false
                        val decodedHeader = BlockHeaderData.fromBinary(nonAnchoredHeaderOp.rawHeader)
                        anchorHeight = decodedHeader.getHeight()
                        merkleHashVersion = decodedHeader.getMerkleHashVersion()
                    }

                    IcmfReceiverSpecialTxExtension.MessageHashOp.OP_NAME -> {
                        val messageHashOp = IcmfReceiverSpecialTxExtension.MessageHashOp.fromOpData(op.asOpData())
                                ?: throw ProgrammerMistake("Invalid message hash operation")

                        // Keep processing if we have any spill information
                        val spillInfo = spillBySenderAndTopic[messageHashOp.sender to messageHashOp.topic] ?: continue

                        if (hasSeenOldestSpillMessage || messageHashOp.hash.contentEquals(spillInfo.oldestSpilledMessageHash)) {
                            hasSeenOldestSpillMessage = true
                            restoredSpilledMessages.add(SpilledMessageWithSenderAndTopic(
                                    spillInfo.spillHeight,
                                    spillInfo.topic,
                                    spillInfo.sender,
                                    cluster,
                                    anchorHeight,
                                    merkleHashVersion,
                                    messageHashOp.hash
                            ))
                        }
                    }
                }
            }
        }

        dbOperations.saveSpilledMessages(ctx, restoredSpilledMessages)
    }

    private fun mapSpilledMessagesDatum(data: Gtv): List<SpilledMessageState> {
        val dataArray = data as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
        return dataArray.array.map {
            val elementArray = it as? GtvArray ?: throw ProgrammerMistake("Invalid data. Must be an array.")
            if (elementArray.getSize() != 4) throw ProgrammerMistake("Invalid data. Must contain 3 elements.")

            SpilledMessageState(
                    elementArray[0].asInteger(),
                    elementArray[1].asString(),
                    BlockchainRid(elementArray[2].asByteArray()),
                    elementArray[3].asByteArray(),
            )
        }
    }

    fun loadLastAnchoredHeight(ctx: EContext, cluster: String, topic: String): Long =
            dbOperations.loadLastAnchoredHeight(ctx, cluster, topic)

    fun loadLastMessageHeight(ctx: EContext, blockchainRid: BlockchainRid, topic: String): Long =
            dbOperations.loadLastMessageHeight(ctx, blockchainRid, topic)

    fun loadSpilledMessageCounts(ctx: EContext, string: String, height: Long, topic: String): Map<BlockchainRid, Int> =
            dbOperations.loadSpilledMessageCounts(ctx, string, height, topic)

    fun loadLastAnchoredHeights(ctx: EContext): List<AnchorHeight> = dbOperations.loadLastAnchoredHeights(ctx)

    fun loadOldestSpilledMessage(ctx: EContext, sender: BlockchainRid, topic: String): SpilledMessage? =
            dbOperations.loadOldestSpilledMessage(ctx, sender, topic)

    fun imprecateSpilledMessage(bctx: BlockEContext, serial: Long) {
        hasNewSpilledMessages = true
        dbOperations.imprecateSpilledMessage(bctx, serial)
    }

    fun saveLastAnchoredHeight(bctx: BlockEContext, cluster: String, topic: String, anchorHeight: Long) {
        hasNewanchoringHeights = true
        dbOperations.saveLastAnchoredHeight(bctx, cluster, topic, anchorHeight)
    }

    fun saveLastAnchoredHeights(bctx: BlockEContext, updatedAnchoringHeights: List<AnchorHeight>) {
        if (updatedAnchoringHeights.isEmpty()) return
        hasNewanchoringHeights = true
        dbOperations.saveLastAnchoredHeights(bctx, updatedAnchoringHeights)
    }

    fun saveSpilledMessages(bctx: BlockEContext, spilledMessageWithSenderAndTopic: List<SpilledMessageWithSenderAndTopic>) {
        hasNewSpilledMessages = true
        dbOperations.saveSpilledMessages(bctx, spilledMessageWithSenderAndTopic)
    }

    fun saveLastMessageHeight(bctx: BlockEContext, blockchainRid: BlockchainRid, topic: String, height: Long) {
        hasNewMessageHeights = true
        dbOperations.saveLastMessageHeight(bctx, blockchainRid, topic, height)
    }
}