package net.postchain.d1.anchoring

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

const val EVENT_TYPE = "icmf_header"
const val ICMF_ANCHOR_HEADERS_EXTRA = "icmf_anchor_headers"

class AnchorBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem

    private val queuedEvents = mutableListOf<AnchorIcmfHeader>()

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        cryptoSystem = baseBB.cryptoSystem
        baseBB.installEventProcessor(EVENT_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        queuedEvents.add(AnchorIcmfHeader.fromGtv(data))
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        val hashCalculator = GtvMerkleHashCalculator(cryptoSystem)
        val hashesByTopic = queuedEvents
                .groupBy { it.topic }
        val hashByTopic = hashesByTopic
                .mapValues {
                    TopicHeaderData(
                            gtv(it.value.map { header -> header.blockRid }).merkleHash(hashCalculator),
                            it.value.first().previousHeight
                    ).toGtv()
                }
        return mapOf(
                ICMF_ANCHOR_HEADERS_EXTRA to gtv(hashByTopic)
        )
    }

    data class AnchorIcmfHeader(
            val topic: String,
            val blockRid: Gtv,
            val previousHeight: Long
    ) {
        companion object {
            fun fromGtv(gtv: Gtv): AnchorIcmfHeader =
                    AnchorIcmfHeader(gtv["topic"]!!.asString(), gtv["block_rid"]!!, gtv["previous_anchor_height"]!!.asInteger())
        }
    }
}
