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
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtv.merkleHash

const val ICMF_MESSAGE_TYPE = "icmf_message"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension(private val isSystemChain: Boolean, private val dbOperations: IcmfDatabaseOperations) : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem

    private val queuedEvents = mutableListOf<SentIcmfMessageItem>()

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        cryptoSystem = baseBB.cryptoSystem
        baseBB.installEventProcessor(ICMF_MESSAGE_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        val message = SentIcmfMessage.fromGtv(data)

        if (!message.topic.startsWith(ICMF_TOPIC_GLOBAL_PREFIX) && !message.topic.startsWith(ICMF_TOPIC_LOCAL_PREFIX)) {
            logger.info("ICMF message with invalid topic ${message.topic} will not be sent")
        } else if (message.topic.startsWith(ICMF_TOPIC_GLOBAL_PREFIX) && !isSystemChain) {
            logger.info("ICMF message with topic ${message.topic} will not be sent from non-system chain")
        } else {
            logger.info("ICMF message sent in topic ${message.topic}")
            dbOperations.saveSentMessage(ctxt, ctxt.txIID, message.topic, ctxt.height, GtvEncoder.encodeGtv(message.body))
            val previousMessageBlockHeight = dbOperations.getPreviousSentMessageBlockHeight(ctxt, message.topic, ctxt.height)
            ctxt.addAfterAppendHook {
                queuedEvents.add(SentIcmfMessageItem(message.topic, message.body, previousMessageBlockHeight))
            }
        }
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        val hashCalculator = GtvMerkleHashCalculatorV1(cryptoSystem)
        return if (queuedEvents.isNotEmpty()) {
            val hashesByTopic = queuedEvents
                    .groupBy { it.topic }
            val hashByTopic = hashesByTopic
                    .mapValues {
                        TopicHeaderData(gtv(
                                it.value.map { message -> gtv(message.body.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
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
