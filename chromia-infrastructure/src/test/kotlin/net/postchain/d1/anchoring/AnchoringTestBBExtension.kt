package net.postchain.d1.anchoring

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.d1.TopicHeaderData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class AnchoringTestBBExtension : BaseBlockBuilderExtension {
    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {

    }

    override fun finalize(): Map<String, Gtv> {
        return mapOf(
                "icmf_send" to gtv(
                        mapOf(
                                "G_my-topic" to TopicHeaderData(AnchoringIT.messagesHash, -1).toGtv()
                        )
                )
        )
    }
}
