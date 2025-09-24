package net.postchain.crypto.webauthn

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.core.TxEContext
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.ExtOpData
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * WebAuthn authenticate.
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API#authenticating_a_user
 */
class CheckSigWebAuthnAuthenticate(conf: Unit, opData: ExtOpData) : GTXOperation(opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.checksig_webauthn_authenticate"

        val metadata = OperationMetadata(args = listOf(
                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/authenticatorData from AuthenticatorAssertionResponse
                ArgumentMetadata("authenticator_data", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAssertionResponse
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),

                // COSE Algorithm Identifier, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKeyAlgorithm
                ArgumentMetadata("alg", setOf(GtvType.INTEGER)),

                // SubjectPublicKeyInfo, https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getPublicKey
                ArgumentMetadata("public_key", setOf(GtvType.BYTEARRAY)),

                // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/signature from AuthenticatorAssertionResponse
                ArgumentMetadata("signature", setOf(GtvType.BYTEARRAY)),
        ))
    }

    override fun apply(ctx: TxEContext) = true

    override fun isCompound() = true

    override fun checkCorrectnessWhileSyncing() {
        verifySignature(data.args)
    }

    override fun checkCorrectness() {
        verifySignature(data.args)
    }

    private fun verifySignature(args: Array<out Gtv>) {
        if (args.size != 5) throw UserMistake("need 5 args, got ${args.size}")
        val authenticatorData = args[0].asByteArray()
        val clientDataJSON = args[1].asString()
        val alg = args[2].asInteger()
        val publicKey = args[3].asByteArray()
        val signature = args[4].asByteArray()

        val signedData = authenticatorData + sha256Digest(clientDataJSON.toByteArray(Charsets.UTF_8))

        val pk = try {
            val key = X509EncodedKeySpec(publicKey)
            val kFact = KeyFactory.getInstance(getKeyAlgorithm(alg))
            kFact.generatePublic(key)
        } catch (e: GeneralSecurityException) {
            throw UserMistake("invalid public key: ${e.message}")
        }

        try {
            val signatureChecker = Signature.getInstance(getSignatureAlgorithm(alg))
            signatureChecker.initVerify(pk)
            signatureChecker.update(signedData)
            if (!signatureChecker.verify(signature)) {
                throw UserMistake("signature verification failed")
            }
        } catch (e: GeneralSecurityException) {
            throw UserMistake("unable to verify signature: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw UserMistake("unable to verify signature: ${e.message}")
        }
    }

    private fun getKeyAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "EC"
        -8L -> "EdDSA"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }

    private fun getSignatureAlgorithm(coseAlgorithm: Long): String = when (coseAlgorithm) {
        -7L -> "SHA256withECDSA"
        -8L -> "ed25519"
        else -> throw UserMistake("unsupported algorithm: $coseAlgorithm")
    }
}
