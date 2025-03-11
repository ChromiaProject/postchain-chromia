package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.GtxOpData
import net.postchain.gtx.data.OpData

class RequestTakenOp(
        val id: String,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation request_taken(id: text)
        const val OP_NAME = "__hc.request_taken"

        fun fromOpData(opData: GtxOpData): RequestTakenOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 1) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                RequestTakenOp(opData.args[0].asString())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id)))
}

class ResponseOp(
        val id: String,
        val type: String,
        val output: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation response(id: text, type: text, output: byte_array)
        const val OP_NAME = "__hc.response"

        fun fromOpData(opData: GtxOpData): ResponseOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 3) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                ResponseOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), gtv(output)))
}

class FailureOp(
        val id: String,
        val type: String,
        val error: String,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation failure(id: text, type: text, error: text)
        const val OP_NAME = "__hc.failure"

        fun fromOpData(opData: GtxOpData): FailureOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 3) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                FailureOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2].asString())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), gtv(error)))
}

sealed interface Computation {
    val type: String
}

class StartedComputation(override val type: String) : Computation

class FinishedComputation(override val type: String, val output: ByteArray) : Computation

class FailedComputation(override val type: String, val errorMessage: String) : Computation
