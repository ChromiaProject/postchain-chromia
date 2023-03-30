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
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

const val ICMF_MESSAGE_TYPE = "icmf_message"
const val ICMF_LOCAL_MESSAGE_TYPE = "icmf_local_message"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"
const val ICMF_LOCAL_BLOCK_HEADER_EXTRA = "icmf_local_send"

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem

    private val queuedEvents = mutableListOf<SentIcmfMessage>()
    private val queuedLocalEvents = mutableListOf<SentIcmfMessage>()

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        cryptoSystem = baseBB.cryptoSystem
        baseBB.installEventProcessor(ICMF_MESSAGE_TYPE, this)
        baseBB.installEventProcessor(ICMF_LOCAL_MESSAGE_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        val message = SentIcmfMessage.fromGtv(data)
        if (ICMF_LOCAL_MESSAGE_TYPE == type) {
            logger.info("Local ICMF message sent in topic ${message.topic}")
            queuedLocalEvents.add(message)
        } else {
            logger.info("ICMF message sent in topic ${message.topic}")
            queuedEvents.add(message)
        }
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        return mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(calculateHashByTopic(queuedEvents, hashCalculator)),
                ICMF_LOCAL_BLOCK_HEADER_EXTRA to gtv(calculateHashByTopic(queuedLocalEvents, hashCalculator))
        )
    }

    private fun calculateHashByTopic(events: MutableList<SentIcmfMessage>, hashCalculator: GtvMerkleHashCalculator): Map<String, Gtv> {
        val hashesByTopic: Map<String, List<SentIcmfMessage>> = events.groupBy { it.topic }
        return hashesByTopic
                .mapValues {
                    TopicHeaderData(gtv(
                            it.value.map { message -> gtv(message.body.merkleHash(hashCalculator)) }).merkleHash(hashCalculator),
                            it.value.first().previousMessageBlockHeight
                    ).toGtv()
                }
    }
}
