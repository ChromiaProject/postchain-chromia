package net.postchain.d1.icmf

internal interface QueuedPipe {
    val queueLength: Int
    val queueSizeBytes: Int
}