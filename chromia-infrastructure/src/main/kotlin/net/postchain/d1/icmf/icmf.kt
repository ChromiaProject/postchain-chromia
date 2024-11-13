package net.postchain.d1.icmf

const val MAX_MESSAGE_SIZE = 16 * 1024 * 1024 // 16 MiB

const val ICMF_TOPIC_GLOBAL_PREFIX = "G_"
const val ICMF_TOPIC_LOCAL_PREFIX = "L_"

fun isValidTopicName(name: String) =
        name.startsWith(ICMF_TOPIC_GLOBAL_PREFIX) || name.startsWith(ICMF_TOPIC_LOCAL_PREFIX)