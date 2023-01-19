package net.postchain.mc.network

import net.postchain.chain0.common.Codename
import net.postchain.chain0.common.directoryVersion

class Version(private val client: net.postchain.client.core.PostchainClient) {

    companion object {
        val initialVersion = net.postchain.chain0.common.Version(Codename.Delta, "0.1.0")
    }

    val version by lazy {
        try {
            client.directoryVersion()
        } catch (_: Throwable) {
            initialVersion
        }
    }
}