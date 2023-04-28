package net.postchain.mc.network

import com.github.ajalt.clikt.core.PrintMessage
import net.postchain.chain0.version.apiVersion
import net.postchain.client.core.PostchainQuery

class Version(private val client: PostchainQuery) {

    val version by lazy {
        try {
            client.apiVersion()
        } catch (_: Throwable) {
            1
        }
    }
}

fun PostchainQuery.requireApiVersion(version: Long) = if (Version(this).version < version) throw PrintMessage("Command not supported by network") else Unit
