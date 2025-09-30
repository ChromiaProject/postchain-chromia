package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CheckSigWebAuthnAuthenticateTest {

    val validClientDataJSON = """{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org"}"""
    val validAuthenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(), true, listOf(), userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("need 5 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("message"),
                gtv("{}"),
                gtv(0),
                gtv(byteArrayOf()),
                gtv(byteArrayOf()),
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(), true, listOf(), userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    @Test
    fun `should throw UserMistake when provided invalid clientDataJSON`() {
        assertFailure {
            checkSignature(validAuthenticatorData, "bogus", -7, byteArrayOf(), byteArrayOf())
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid clientData")
    }

    @Test
    fun `should throw UserMistake when provided invalid authenticatorData`() {
        assertFailure {
            checkSignature(byteArrayOf(), validClientDataJSON, 7, byteArrayOf(), byteArrayOf())
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid authenticatorData")
    }

    @Test
    fun `should throw UserMistake when crossOrigin does not match`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validAuthenticatorData),
                    gtv("""{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(-7),
                    gtv(byteArrayOf()),
                    gtv(byteArrayOf())
            )
            val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, listOf("example.org", "webauthn.io"), userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("crossOrigin is set but now allowed")
    }

    @Test
    fun `should throw UserMistake when user is not verified`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validAuthenticatorData),
                    gtv("""{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org"}"""),
                    gtv(-7),
                    gtv(byteArrayOf()),
                    gtv(byteArrayOf())
            )
            val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, listOf("example.org", "webauthn.io"), userPresence = true, userVerification = true), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("user verification is required, but user is not verified")
    }

    @Test
    fun `should throw UserMistake when provided unsupported algorithm`() {
        assertFailure {
            checkSignature(validAuthenticatorData, validClientDataJSON, 0, byteArrayOf(), byteArrayOf())
        }.isInstanceOf(UserMistake::class.java).messageContains("unsupported algorithm")
    }

    @Nested
    inner class TestVector {
        /**
         * https://w3c.github.io/webauthn/#sctn-test-vectors-none-es256
         */
        val authenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()
        val clientDataJSON = String("7b2274797065223a22776562617574686e2e676574222c226368616c6c656e6765223a224f63446e55685158756c5455506f334a5558543049393770767a7a59425039745a63685879617630314167222c226f726967696e223a2268747470733a2f2f6578616d706c652e6f7267222c2263726f73734f726967696e223a66616c73657d".hexStringToByteArray())
        val signature = "3046022100f50a4e2e4409249c4a853ba361282f09841df4dd4547a13a87780218deffcd380221008480ac0f0b93538174f575bf11a1dd5d78c6e486013f937295ea13653e331e87".hexStringToByteArray()
        val publicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

        @Test
        fun `should success with valid signature`() {
            assertDoesNotThrow { checkSignature(authenticatorData, clientDataJSON, -7, publicKey, signature) }
        }

        @Test
        fun `should throw UserMistake when origin does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(authenticatorData),
                        gtv(clientDataJSON),
                        gtv(-7),
                        gtv(publicKey),
                        gtv(signature)
                )
                val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://bogus.org"), Origin("https://webauthn.io")), true, listOf("example.org", "webauthn.io"), userPresence = false, userVerification = false), opData).checkCorrectness()
            }.isInstanceOf(UserMistake::class.java).messageContains("origin does not match")
        }

        @Test
        fun `should throw UserMistake relying party identifier does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(authenticatorData),
                        gtv(clientDataJSON),
                        gtv(-7),
                        gtv(publicKey),
                        gtv(signature)
                )
                val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                CheckSigWebAuthnAuthenticate(WebAuthnConfig(listOf(), true, listOf("bogus.org", "webauthn.io"), userPresence = false, userVerification = false), opData).checkCorrectness()
            }.isInstanceOf(UserMistake::class.java).messageContains("relying party identifier does not match")
        }

        @Test
        fun `should throw UserMistake when signature is wrong`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, publicKey, wrongSignature)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with wrong signature`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, publicKey, wrongSignature)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with wrong public key`() {
            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, wrongPublicKey, signature)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with null public key`() {
            val nullPublicKey = ByteArray(publicKey.size)
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, nullPublicKey, signature)
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }

        @Test
        fun `should throw UserMistake when provided with invalid public key`() {
            val invalidPublicKey = ByteArray(32) { it.toByte() }
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, invalidPublicKey, signature)
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }
    }

    @Nested
    inner class Yubikey {
        val objectConverter = ObjectConverter()

        @Test
        fun `ECDSA -7 success`() {
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val (authenticatorData, clientDataJSON, signature) = extractAuthenticationData("/net/postchain/crypto/webauthn/authentication-7.json")

            assertDoesNotThrow {
                checkSignature(authenticatorData, clientDataJSON, -7, publicKey, signature)
            }
        }

        @Test
        fun `ECDSA -7 wrong signature`() {
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val (authenticatorData, clientDataJSON, signature) = extractAuthenticationData("/net/postchain/crypto/webauthn/authentication-7.json")

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -7, publicKey, wrongSignature)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `EdDSA -8 success`() {
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val (authenticatorData, clientDataJSON, signature) = extractAuthenticationData("/net/postchain/crypto/webauthn/authentication-8.json")

            assertDoesNotThrow {
                checkSignature(authenticatorData, clientDataJSON, -8, publicKey, signature)
            }
        }

        @Test
        fun `EdDSA -8 wrong signature`() {
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val (authenticatorData, clientDataJSON, signature) = extractAuthenticationData("/net/postchain/crypto/webauthn/authentication-8.json")

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkSignature(authenticatorData, clientDataJSON, -8, publicKey, wrongSignature)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        private fun extractPublicKey(registrationResourcePath: String): ByteArray {
            val registrationResponseJSON = javaClass.getResourceAsStream(registrationResourcePath)!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            return registrationResponse!!.response!!.publicKey!!
        }

        private fun extractAuthenticationData(authenticationResourcePath: String): Triple<ByteArray, String, ByteArray> {
            val authenticationResponseJSON = javaClass.getResourceAsStream(authenticationResourcePath)!!
            val authenticationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val authenticatorData = authenticationResponse!!.response!!.authenticatorData
            val clientDataJSON = authenticationResponse.response!!.clientDataJSON
            val signature = authenticationResponse.response!!.signature
            return Triple(authenticatorData, String(clientDataJSON), signature)
        }
    }

    private fun checkSignature(authenticatorData: ByteArray, clientDataJSON: String, alg: Long, publicKey: ByteArray, signature: ByteArray) {
        val args = arrayOf(
                gtv(authenticatorData),
                gtv(clientDataJSON),
                gtv(alg),
                gtv(publicKey),
                gtv(signature)
        )
        val opData = ExtOpData(CheckSigWebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        CheckSigWebAuthnAuthenticate(WebAuthnConfig(
                listOf(Origin("https://example.org"), Origin("https://webauthn.io")),
                false,
                listOf("example.org", "webauthn.io"),
                userPresence = true,
                userVerification = false
        ), opData).checkCorrectness()
    }
}
