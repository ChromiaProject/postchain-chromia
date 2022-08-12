package net.postchain.mc.cli.base

import net.postchain.client.core.*
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.SigMaker
import net.postchain.mc.config.app.ClientConfig

object ClientUtil {

    fun fromConfig(config: ClientConfig): PostchainClient {
        if (config.privKey.isEmpty() || config.brid.isEmpty() || config.pubKey.isEmpty()) {
            throw UserMistake("Missing required parameters: brid | pub-key | priv-key")
        }
        val sigMaker = sigMaker(config)
        val defaultSigner = DefaultSigner(sigMaker, config.pubKey.hexStringToByteArray())

        return ConcretePostchainClientProvider().createClient(config.apiURL, BlockchainRid.buildFromHex(config.brid), defaultSigner)
    }

    fun sigMaker(config: ClientConfig): SigMaker {
        return cryptoSystem.buildSigMaker(config.pubKey.hexStringToByteArray(), config.privKey.hexStringToByteArray())
    }
}
