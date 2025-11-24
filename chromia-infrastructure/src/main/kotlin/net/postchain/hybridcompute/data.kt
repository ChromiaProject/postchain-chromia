package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.GtxOpData
import net.postchain.gtx.data.OpData

class RequestTakenOp(
        val id: String,
        val type: String,
        val processedBy: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation request_taken(id: text, type: text, processed_by: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.request_taken"

        fun fromOpData(opData: GtxOpData): RequestTakenOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 4) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                RequestTakenOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2].asByteArray(), opData.args[3].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), gtv(processedBy), gtv(signatureData)))
}

class ResponseOp(
        val id: String,
        val type: String,
        val input: Gtv,
        val output: Gtv,
        val signatureSubjectId: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation response(id: text, type: text, input: gtv, output: gtv, signature_subject_id: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.response"

        fun fromOpData(opData: GtxOpData): ResponseOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 6) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                ResponseOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2], opData.args[3], opData.args[4].asByteArray(), opData.args[5].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), input, output, gtv(signatureSubjectId), gtv(signatureData)))
}

class FailureOp(
        val id: String,
        val type: String,
        val input: Gtv,
        val error: String,
        val signatureSubjectId: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation failure(id: text, type: text, input: gtv, error: text, signature_subject_id: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.failure"

        fun fromOpData(opData: GtxOpData): FailureOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 6) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                FailureOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2], opData.args[3].asString(), opData.args[4].asByteArray(), opData.args[5].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), input, gtv(error), gtv(signatureSubjectId), gtv(signatureData)))
}

class ClusterTimeoutOp(
        val id: String,
        val type: String,
        val input: Gtv,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation cluster_timeout(id: text, type: text, input: gtv)
        const val OP_NAME = "__hc.cluster_timeout"

        fun fromOpData(opData: GtxOpData): ClusterTimeoutOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 3) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                ClusterTimeoutOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2])
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), input))
}

sealed interface Computation {
    val type: String
    val input: Gtv
}

data class TakenComputation(override val type: String, override val input: Gtv) : Computation

data class StartedComputation(override val type: String, override val input: Gtv) : Computation

data class FinishedComputation(override val type: String, override val input: Gtv, val output: Gtv) : Computation

data class FailedComputation(override val type: String, override val input: Gtv, val errorMessage: String) : Computation
