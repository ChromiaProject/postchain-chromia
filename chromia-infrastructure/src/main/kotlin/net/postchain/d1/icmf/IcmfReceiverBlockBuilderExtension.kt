package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.GtvObjectMapper

const val ICMF_RECEIVER_TOPICS_EVENT_TYPE = "icmf_receiver_topics"

/**
 * Installs the event processor for ICMF receiver topic configuration events.
 */
class IcmfReceiverBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem
    private val chainEventListeners = mutableMapOf<Long, (List<IcmfReceiverTopicEventMessage>) -> Unit>()

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        cryptoSystem = baseBB.cryptoSystem
        baseBB.installEventProcessor(ICMF_RECEIVER_TOPICS_EVENT_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {

        val topics = try {
            GtvObjectMapper.fromArray(data, IcmfReceiverTopicEventMessage::class.java)
        } catch (e: Exception) {
            throw UserMistake("Received invalid icmf topics: ${e.message}", e)
        }

        topics.map {
            if (!isValidTopicName(it.topic)) {
                throw UserMistake("Invalid topic name: ${it.topic}")
            }

            if (it.topic.startsWith(ICMF_TOPIC_LOCAL_PREFIX) && it.bcRid == null) {
                throw UserMistake("Local topic requires blockchain rid: ${it.topic}")
            }
        }

        ctxt.addAfterAppendHook {

            logger.debug { "Dapp requests ICMF receiver topics update: ${ctxt.height}: $topics" }

            chainEventListeners[ctxt.chainID]?.invoke(topics)
        }
    }

    fun addEventListener(chainId: Long, function: (List<IcmfReceiverTopicEventMessage>) -> Unit) {
        chainEventListeners[chainId] = function
    }

    override fun finalize(): Map<String, Gtv> = mapOf()

    fun removeEventListener(chainID: Long) {
        chainEventListeners.remove(chainID)
    }
}
