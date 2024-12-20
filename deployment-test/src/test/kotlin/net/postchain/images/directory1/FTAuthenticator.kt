package net.postchain.images.directory1

import net.postchain.chain0.lib.ft4.external.auth.ftAuthOperation
import net.postchain.chain0.lib.ft4.external.auth.getAuthFlags
import net.postchain.client.core.PostchainClient
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.eif.lib.ft4.external.accounts.Ft4GetAccountMainAuthDescriptorResult
import net.postchain.eif.lib.ft4.external.accounts.getAccountMainAuthDescriptor
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtv.merkleHash

class FTAuthenticator(
        val keyPair: KeyPair,
        val client: PostchainClient
) {

    val accountId: ByteArray = gtv(keyPair.pubKey.data).merkleHash(hashCalculator)
    val authDescriptor: Ft4GetAccountMainAuthDescriptorResult = client.getAccountMainAuthDescriptor(accountId)

    companion object {
        private val hashCalculator = GtvMerkleHashCalculatorV1(Secp256K1CryptoSystem())
    }

    fun transactionBuilder(): TransactionBuilder = client.transactionBuilder()
            .ftAuthOperation(accountId, authDescriptor.id.data)

    fun verifyOperationAuthFlags(opName: String) {
        val opAuthFlags = client.getAuthFlags(opName)
        authDescriptor.requireAuthFlags(opAuthFlags, opName)
    }

    private fun Ft4GetAccountMainAuthDescriptorResult.requireAuthFlags(operationAuthFlags: List<String>, opName: String) {
        val flags = args.asArray().first().asArray().map { it.asString() }
        if (!flags.containsAll(operationAuthFlags)) {
            throw FTAuthenticatorException(
                    "No valid account descriptor found. Operation $opName requires the flag(s): $operationAuthFlags, " +
                            "while the flag(s) of the auth descriptor is: $flags"
            )
        }
    }
}

class FTAuthenticatorException(message: String) : RuntimeException(message)
