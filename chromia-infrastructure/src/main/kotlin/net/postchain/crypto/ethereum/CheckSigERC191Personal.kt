package net.postchain.crypto.ethereum

import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.OperationMetadata
import net.postchain.gtx.data.ExtOpData
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import java.security.SignatureException

/**
 * Ethereum [ERC-191](https://eips.ethereum.org/EIPS/eip-191) personal_sign.
 */
class CheckSigERC191Personal(conf: Unit, opData: ExtOpData) : GTXOperation(opData) {
    companion object {
        const val OP_NAME = "gtxc.checksig_secp256k1_erc191_personal"

        val metadata = OperationMetadata(args = listOf(
                ArgumentMetadata("message", setOf(GtvType.STRING)), // plain message to sign
                ArgumentMetadata("address", setOf(GtvType.BYTEARRAY)), // Ethereum address (20 bytes)
                ArgumentMetadata("signature", setOf(GtvType.BYTEARRAY)), // signature (64 or 65 bytes)
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
        if (args.size != 3) throw UserMistake("need 3 args, got ${args.size}")
        val message = args[0].asString()
        val address = args[1].asByteArray()
        val signature = args[2].asByteArray()

        try {
            val publicKey = Sign.signedPrefixedMessageToKey(message.toByteArray(Charsets.UTF_8), Sign.signatureDataFromHex(signature.toHex()))
            val recoveredAddress = Keys.getAddress(publicKey).hexStringToByteArray()
            if (!address.contentEquals(recoveredAddress)) throw UserMistake("signature verification failed")
        } catch (e: SignatureException) {
            throw UserMistake(e.message ?: "unable to check signature")
        }
    }
}
