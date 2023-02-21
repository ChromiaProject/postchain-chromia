package net.postchain.d1.anchoring

interface AnchoringReceiver {
    val localPipes: MutableMap<Long, AnchoringPipe>
    fun getRelevantPipes(): List<AnchoringPipe>
}