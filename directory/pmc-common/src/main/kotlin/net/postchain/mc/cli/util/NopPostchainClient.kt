package net.postchain.mc.cli.util

import net.postchain.client.core.PostchainClient
import net.postchain.crypto.KeyPair

/**
 * Postchain client that adds a no-op to each [TransactionBuilder]
 */
class NopPostchainClient(val client: PostchainClient) : PostchainClient by client {
    override fun transactionBuilder() = client.transactionBuilder().addNop()
    override fun transactionBuilder(signers: List<KeyPair>) = client.transactionBuilder(signers).addNop()
}
