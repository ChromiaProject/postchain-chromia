package net.postchain.mc.network

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