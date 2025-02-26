package net.postchain.nn

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import java.util.concurrent.ConcurrentHashMap


class NNRequest(
        @Name("id")
        val id: String,
        @Name("text")
        val text: String
)


class NNSpecialTransactionExtension: GTXSpecialTxExtension {

    lateinit var module: GTXModule

    override fun getRelevantOps(): Set<String> {
        return setOf("__nn_response", "__nn_take_request")
    }

    private class Response (val request: NNRequest, val response: String)

    private val pendingResponses = ConcurrentHashMap<String, Response>()

    // thread pool
    val executor = java.util.concurrent.Executors.newFixedThreadPool(1)


    val nnTextModel = DJLTextModel()

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        this.module = module
    }
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return position == SpecialTransactionPosition.Begin
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (position != SpecialTransactionPosition.Begin) return listOf()
        val retval = mutableListOf<OpData>()
        val rqs = this.module.query(bctx, "nn.get_requests",
                GtvDictionary.build(mapOf()))

        for (r in pendingResponses.values) {
            retval.add(OpData("__nn_response",
                    arrayOf(gtv(r.request.id), gtv(r.response))
            ))
            bctx.addAfterCommitHook { pendingResponses.remove(r.request.id) }
        }

        for (r in rqs.asArray()) {
            val request = GtvObjectMapper.fromGtv(r, NNRequest::class)
            retval.add(OpData("__nn_take_request",
                    arrayOf(gtv(request.id))
            ))
            executor.submit {
                val responseText = nnTextModel.generateText(request.text)
                val response = Response(request, responseText)
                pendingResponses[request.id] = response
            }
        }
        return retval
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        // TODO: __nn_response should be validated using the verifier (synchronously?)
        // TODO: __nn_take_request correctness is going to be validated in Rell code so it
        //       can be accepted as is
        return true
    }

}