package net.postchain.mc.mcu.model

data class Node(
    val name: String,
    val key: String,
    val host: String,
    val port: Long,
    val apiUrl: String,
    val active: Boolean
)
