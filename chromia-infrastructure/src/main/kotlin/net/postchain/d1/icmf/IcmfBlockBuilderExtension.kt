package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.TopicHeaderData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkleHash

const val ICMF_MESSAGE_TYPE = "icmf_message"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension(private val isSystemChain: Boolean, private val dbOperations: IcmfSenderDatabaseOperations) : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem
    private lateinit var merkleHashCalculator: GtvMerkleHashCalculatorBase

    private val queuedEvents = mutableListOf<SentIcmfMessageItem>()

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        cryptoSystem = baseBB.cryptoSystem
        merkleHashCalculator = baseBB.merkleHashCalculator
        baseBB.installEventProcessor(ICMF_MESSAGE_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        val message = SentIcmfMessage.fromGtv(data)

        if (!isValidTopicName(message.topic)) {
            logger.info("ICMF message with invalid topic will not be sent")
            return
        }

        if (message.topic.startsWith(ICMF_TOPIC_GLOBAL_PREFIX) && !isSystemChain) {
            logger.info("ICMF message with topic ${message.topic} will not be sent from non-system chain")
            return
        }

        val encodedBody = GtvEncoder.encodeGtv(message.body)
        if (encodedBody.size > ICMF_MESSAGE_MAX_SIZE) {
            logger.info("ICMF message with topic ${message.topic} and too big body will not be sent")
            return
        }

        logger.info("ICMF message sent in topic ${message.topic}")
        dbOperations.saveSentMessage(ctxt, ctxt.txIID, message.topic, ctxt.height, encodedBody)
        val previousMessageBlockHeight = dbOperations.getPreviousSentMessageBlockHeight(ctxt, message.topic, ctxt.height)
        ctxt.addAfterAppendHook {
            queuedEvents.add(SentIcmfMessageItem(message.topic, message.body, previousMessageBlockHeight))
        }
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        return if (queuedEvents.isNotEmpty()) {
            val hashesByTopic = queuedEvents
                    .groupBy { it.topic }
            val hashByTopic = hashesByTopic
                    .mapValues {
                        TopicHeaderData(gtv(
                                it.value.map { message -> gtv(message.body.merkleHash(merkleHashCalculator)) }).merkleHash(merkleHashCalculator),
                                it.value.first().previousMessageBlockHeight
                        ).toGtv()
                    }
            mapOf(ICMF_BLOCK_HEADER_EXTRA to gtv(hashByTopic))
        } else {
            mapOf()
        }
    }

    private data class SentIcmfMessageItem(
            val topic: String, // Topic of message
            val body: Gtv,
            val previousMessageBlockHeight: Long
    )
}
