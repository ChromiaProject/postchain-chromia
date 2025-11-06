package net.postchain.hybridcompute

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.GtxOpData
import net.postchain.gtx.data.OpData

class RequestTakenOp(
        val id: String,
        val processedBy: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation request_taken(id: text, processed_by: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.request_taken"

        fun fromOpData(opData: GtxOpData): RequestTakenOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 3) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                RequestTakenOp(opData.args[0].asString(), opData.args[1].asByteArray(), opData.args[2].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(processedBy), gtv(signatureData)))
}

class ResponseOp(
        val id: String,
        val type: String,
        val output: Gtv,
        val signatureSubjectId: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation response(id: text, type: text, output: gtv, signature_subject_id: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.response"

        fun fromOpData(opData: GtxOpData): ResponseOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 5) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                ResponseOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2], opData.args[3].asByteArray(), opData.args[4].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), output, gtv(signatureSubjectId), gtv(signatureData)))
}

class FailureOp(
        val id: String,
        val type: String,
        val error: String,
        val signatureSubjectId: ByteArray,
        val signatureData: ByteArray,
) {
    companion object : KLogging() {
        // @mount('__hc.') operation failure(id: text, type: text, error: text, signature_subject_id: byte_array, signature_data: byte_array)
        const val OP_NAME = "__hc.failure"

        fun fromOpData(opData: GtxOpData): FailureOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 5) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                FailureOp(opData.args[0].asString(), opData.args[1].asString(), opData.args[2].asString(), opData.args[3].asByteArray(), opData.args[4].asByteArray())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type), gtv(error), gtv(signatureSubjectId), gtv(signatureData)))
}

class ClusterTimeoutOp(
        val id: String,
        val type: String
) {
    companion object : KLogging() {
        // @mount('__hc.') operation cluster_timeout(id: text, type: text, error: text)
        const val OP_NAME = "__hc.cluster_timeout"

        fun fromOpData(opData: GtxOpData): ClusterTimeoutOp? {
            if (opData.opName != OP_NAME) return null
            if (opData.args.size != 2) {
                logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                return null
            }

            return try {
                ClusterTimeoutOp(opData.args[0].asString(), opData.args[1].asString())
            } catch (e: UserMistake) {
                logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                null
            }
        }
    }

    fun toOpData() = OpData(OP_NAME, arrayOf(gtv(id), gtv(type)))
}

sealed interface Computation {
    val type: String
}

class TakenComputation(override val type: String) : Computation

class StartedComputation(override val type: String) : Computation

class FinishedComputation(override val type: String, val output: Gtv) : Computation

class FailedComputation(override val type: String, val errorMessage: String) : Computation
