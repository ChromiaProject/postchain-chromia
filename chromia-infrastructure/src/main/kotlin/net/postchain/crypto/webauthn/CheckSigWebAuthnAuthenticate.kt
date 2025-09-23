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
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * WebAuthn authenticate.
 */
class CheckSigWebAuthnAuthenticate(conf: Unit, opData: ExtOpData) : GTXOperation(opData) {
    companion object : KLogging() {
        const val OP_NAME = "gtxc.checksig_webauthn_authenticate"

        val metadata = OperationMetadata(args = listOf(
                ArgumentMetadata("authenticator_data", setOf(GtvType.BYTEARRAY)),
                ArgumentMetadata("client_data_json", setOf(GtvType.STRING)),
                ArgumentMetadata("public_key", setOf(GtvType.BYTEARRAY)),
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
        if (args.size != 4) throw UserMistake("need 4 args, got ${args.size}")
        val authenticatorData = args[0].asByteArray()
        val clientDataJSON = args[1].asString()
        val publicKey = args[2].asByteArray()
        val signature = args[3].asByteArray()

        val signedData = authenticatorData + sha256Digest(clientDataJSON.toByteArray(Charsets.UTF_8))

        val pk = try {
            val key = X509EncodedKeySpec(publicKey)
            val kFact = KeyFactory.getInstance("EC")
            kFact.generatePublic(key)
        } catch (_: GeneralSecurityException) {
            throw UserMistake("invalid public key")
        }

        if (!verify(signedData, pk, signature)) {
            throw UserMistake("signature verification failed")
        }
    }

    private fun verify(signedData: ByteArray, publicKey: PublicKey, signature: ByteArray): Boolean {
        return try {
            val signatureChecker = Signature.getInstance("SHA256withECDSA")
            signatureChecker.initVerify(publicKey)
            signatureChecker.update(signedData)
            signatureChecker.verify(signature)
        } catch (e: GeneralSecurityException) {
            logger.warn(e) { "Unable to verify WebAuthn signature: ${e.message}" }
            false
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Unable to verify WebAuthn signature: ${e.message}" }
            false
        }
    }
}