package net.postchain.mc.cli.base

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.*
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.SigMaker
import net.postchain.mc.config.app.ClientConfig

object ClientUtil {

    fun fromConfig(config: PostchainClientConfig): PostchainClient {
        return ConcretePostchainClientProvider().createClient(config)
    }
}
