package net.postchain.mc.network

import net.postchain.chain0.common.Codename
import net.postchain.chain0.common.directoryVersion
import net.postchain.chain0.common.Version as RellVersion

class Version(private val client: net.postchain.client.core.PostchainClient) {

    companion object {
        val Delta = RellVersion(Codename.Delta, "0.1.0")
        val Delta_v0_2_0 = RellVersion(Codename.Delta, "0.2.0")
    }

    val version by lazy {
        try {
            client.directoryVersion()
        } catch (_: Throwable) {
            Delta
        }
    }
}