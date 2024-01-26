package net.postchain.images.directory1

import net.postchain.chain0.lib.ft4.accounts.external.getAccountsByParticipantId
import net.postchain.chain0.lib.ft4.auth.external.ftAuthOperation
import net.postchain.chain0.lib.ft4.auth.external.getAuthFlags
import net.postchain.client.core.PostchainClient
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.toList

class FTAuthenticator(private val client: PostchainClient) {

    lateinit var accountId: ByteArray; private set
    private lateinit var authDescriptor: AuthDescriptor; private set

    fun init() {
        accountId = findAccountId()
        authDescriptor = findAuthDescriptor(accountId)
    }

    fun ftAuth(txBuilder: TransactionBuilder) {
        txBuilder.ftAuthOperation(accountId, authDescriptor.id.data)
    }

    fun verifyOperationAuthFlags(opName: String) {
        val operationAuthFlags = client.getAuthFlags(opName)
        authDescriptor.requireAuthFlags(operationAuthFlags, opName)
    }

    private fun findAccountId(): ByteArray {
        val pubKey = client.config.signers.first().pubKey
        val accountIds = client.getAccountsByParticipantId(pubKey.data)
        if (accountIds.isEmpty()) throw FTAuthenticatorException("No accounts found for pubkey: $pubKey")
        return if (accountIds.size > 1) {
            throw FTAuthenticatorException("More than one account found")
        } else accountIds.first()
    }

    private fun findAuthDescriptor(accountId: ByteArray): AuthDescriptor {
        val pubKey = client.config.signers.first().pubKey
        // Cannot use client.getAccountAuthDescriptorsByParticipantId because it cannot handle that the field "rules" is null
        val authDescriptors = client.query(
                "ft4.get_account_auth_descriptors_by_participant_id",
                gtv(mapOf("account_id" to GtvByteArray(accountId), "participant_id" to gtv(pubKey.data)))
        )
        return authDescriptors.toList<AuthDescriptor>().find { it.args[1].asByteArray().wrap() == pubKey.wData }
                ?: throw FTAuthenticatorException("No valid account descriptor found.")
    }

    data class AuthDescriptor(
            @Name("id") val id: WrappedByteArray,
            @Name("args") val args: Gtv,
            @Name("created") val created: Long,
            @Name("auth_type") val authType: String,
            @Name("rules") @Nullable val rules: Gtv?
    ) {
        private val flags by lazy { args.asArray().first().asArray().map { it.asString() } }

        fun requireAuthFlags(operationAuthFlags: List<String>, opName: String) {
            if (!flags.containsAll(operationAuthFlags)) {
                throw FTAuthenticatorException("No valid account descriptor found. Operation $opName requires the flag(s): $operationAuthFlags, while the flag(s) of the auth descriptor is: $flags")
            }
        }
    }
}

class FTAuthenticatorException(message: String) : RuntimeException(message)
