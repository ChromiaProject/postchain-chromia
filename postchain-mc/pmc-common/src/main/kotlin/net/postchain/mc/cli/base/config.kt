package net.postchain.mc.cli.base

import net.postchain.client.config.PostchainClientConfig

fun PostchainClientConfig.pubkey() = signers.first().pubKey