package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid

const val ICMF_MESSAGE_MAX_SIZE = 16 * 1024 * 1024 // 16 MiB

const val ICMF_TOPIC_MAX_SIZE = 250
const val ICMF_TOPIC_GLOBAL_PREFIX = "G_"
const val ICMF_TOPIC_LOCAL_PREFIX = "L_"
const val ICMF_TOPIC_RECEIVER_PREFIX = "R_"

fun isValidTopicName(name: String) = name.length <= ICMF_TOPIC_MAX_SIZE &&
        (name.startsWith(ICMF_TOPIC_GLOBAL_PREFIX) || name.startsWith(ICMF_TOPIC_LOCAL_PREFIX))

fun topicWithReceiver(baseTopic: String, receiver: BlockchainRid): String = "$ICMF_TOPIC_RECEIVER_PREFIX${receiver.toHex()}_$baseTopic"
