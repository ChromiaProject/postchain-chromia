package net.postchain.mc.cli.base

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient


val PostchainClient.pubkey get() = config.pubkey().data

fun PostchainClientConfig.pubkey() = signers.first().pubKey