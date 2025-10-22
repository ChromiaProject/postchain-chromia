package net.postchain.d1.icmf

// @mount("icmf.") query receiver_rate_limit(sender: byte_array, topic: text)

const val RECEIVER_RATE_LIMIT_QUERY_NAME = "icmf.receiver_rate_limit"

class ReceiverRateLimitRequest(
        val sender: ByteArray,
        val topic: String,
)
