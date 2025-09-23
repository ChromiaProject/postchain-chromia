package net.postchain.crypto

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign

class CheckSigERC191PersonalTest {

    val message = "räksmörgås" // message with non-ASCII characters
    val anotherMessage = "AnotherMessage"
    val keyPair: ECKeyPair = Keys.createEcKeyPair()
    val address = Keys.getAddress(keyPair).hexStringToByteArray()
    val anotherKeyPair: ECKeyPair = Keys.createEcKeyPair()
    val anotherAddress = Keys.getAddress(anotherKeyPair).hexStringToByteArray()

    @Test
    fun `should succeed with valid signature`() {
        val signatureData = Sign.signPrefixedMessage(
                message.toByteArray(Charsets.UTF_8),
                keyPair
        )
        val signature = encodeSignature(signatureData)

        assertDoesNotThrow { checkSignature(message, address, signature) }
    }

    @Test
    fun `should throw UserMistake when signature is invalid`() {
        val signatureData = Sign.signPrefixedMessage(
                message.toByteArray(Charsets.UTF_8),
                keyPair
        )
        val signature = encodeSignature(signatureData)

        assertFailure {
            checkSignature(anotherMessage, address, signature)
        }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
    }

    @Test
    fun `should throw UserMistake when provided with wrong address`() {
        val signatureData = Sign.signPrefixedMessage(
                message.toByteArray(Charsets.UTF_8),
                keyPair
        )
        val signature = encodeSignature(signatureData)

        assertFailure {
            checkSignature(message, anotherAddress, signature)
        }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect signature`() {
        assertFailure {
            checkSignature(message, address, "ABCD".hexStringToByteArray())
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid signature string")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(CheckSigERC191Personal.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigERC191Personal(Unit, opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("need 3 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("message".toByteArray()),
                gtv(byteArrayOf()),
                gtv(byteArrayOf()),
        )
        val opData = ExtOpData(CheckSigERC191Personal.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigERC191Personal(Unit, opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("Type error")
    }

    private fun encodeSignature(signatureData: Sign.SignatureData): ByteArray =
            signatureData.r + signatureData.s + signatureData.v

    private fun checkSignature(message: String, address: ByteArray, signature: ByteArray) {
        val args = arrayOf(
                gtv(message),
                gtv(address),
                gtv(signature)
        )
        val opData = ExtOpData(CheckSigERC191Personal.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        CheckSigERC191Personal(Unit, opData).checkCorrectness()
    }
}
