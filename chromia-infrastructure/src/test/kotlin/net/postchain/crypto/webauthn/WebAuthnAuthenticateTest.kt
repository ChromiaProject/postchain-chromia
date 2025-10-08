package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.core.MockEContext
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebAuthnAuthenticateTest {

    val validId = "f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4".hexStringToByteArray()
    val validClientDataJSON = """{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org"}"""
    val validAuthenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()
    val validAlg = -7L
    val validPublicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

    val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    val webAuthnRepository: WebAuthnRepository = mock {
        on { fetchCredential(any(), eq(validId)) } doReturn CredentialData(
                id = validId.wrap(),
                alg = validAlg,
                publicKey = validPublicKey.wrap(),
                signCount = 1,
                transports = "usb,nfc",
                uvInitialized = false,
                backupEligible = true,
                backupState = true,
        )
    }
    val ctx = MockEContext(0)
    val blockCtx = BaseBlockEContext(ctx, 0, 0, 0, mapOf()) { _, _, _ -> }
    val txCtx = BaseTxEContext(blockCtx, 0, TestTransaction(0))

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            WebAuthnAuthenticate(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("need 4 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("message"),
                gtv("{}"),
                gtv(0),
                gtv(byteArrayOf()),
        )
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            WebAuthnAuthenticate(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    @Test
    fun `should throw UserMistake when provided invalid clientDataJSON`() {
        assertFailure {
            checkAuthentication("example.org", validId, validAuthenticatorData, "bogus", byteArrayOf(), 0, false)
        }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
    }

    @Test
    fun `should throw UserMistake when provided invalid authenticatorData`() {
        assertFailure {
            checkAuthentication("example.org", validId, byteArrayOf(), validClientDataJSON, byteArrayOf(), 0, false)
        }.isInstanceOf(UserMistake::class.java).messageContains("provided data does not have proper byte layout")
    }

    @Test
    fun `should throw UserMistake when crossOrigin does not match`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAuthenticatorData),
                    gtv("""{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(byteArrayOf())
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Cross-origin request is prohibited")
    }

    @Test
    fun `should throw UserMistake when user is not verified`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAuthenticatorData),
                    gtv(validClientDataJSON),
                    gtv(byteArrayOf())
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = true, userVerification = true, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Verifier is configured to check user verified, but UV flag in authenticatorData is not set")
    }

    @Test
    fun `should throw UserMistake when user is not registered`() {
        val invalidId = "ABCD".hexStringToByteArray()
        assertFailure {
            val args = arrayOf(
                    gtv(invalidId),
                    gtv(validAuthenticatorData),
                    gtv(validClientDataJSON),
                    gtv(byteArrayOf())
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = true, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("credential with id ${invalidId.toHex()} not registered")
    }

    @Nested
    inner class TestVector {
        /**
         * https://w3c.github.io/webauthn/#sctn-test-vectors-none-es256
         */
        val credentialId = "f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4".hexStringToByteArray()
        val authenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()
        val clientDataJSON = String("7b2274797065223a22776562617574686e2e676574222c226368616c6c656e6765223a224f63446e55685158756c5455506f334a5558543049393770767a7a59425039745a63685879617630314167222c226f726967696e223a2268747470733a2f2f6578616d706c652e6f7267222c2263726f73734f726967696e223a66616c73657d".hexStringToByteArray())
        val signature = "3046022100f50a4e2e4409249c4a853ba361282f09841df4dd4547a13a87780218deffcd380221008480ac0f0b93538174f575bf11a1dd5d78c6e486013f937295ea13653e331e87".hexStringToByteArray()
        val alg = -7L
        val publicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

        @BeforeEach
        fun setup() {
            whenever(webAuthnRepository.fetchCredential(any(), eq(credentialId))) doReturn CredentialData(
                    id = credentialId.wrap(),
                    alg = alg,
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )
        }

        @Test
        fun `should success with valid signature`() {
            assertDoesNotThrow { checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, signature, 0, true) }
        }

        @Test
        fun `should throw UserMistake when origin does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(credentialId),
                        gtv(authenticatorData),
                        gtv(clientDataJSON),
                        gtv(signature)
                )
                val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://bogus.org"), Origin("https://webauthn.io")), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
            }.isInstanceOf(UserMistake::class.java).messageContains("The collectedClientData 'https://example.org' origin doesn't match any of the preconfigured server origin")
        }

        @Test
        fun `should throw UserMistake relying party identifier does not match`() {
            assertFailure {
                checkAuthentication("bogus.org", credentialId, authenticatorData, clientDataJSON, signature, 0, false)
            }.isInstanceOf(UserMistake::class.java).messageContains("rpIdHash doesn't match the hash of preconfigured rpId")
        }

        @Test
        fun `should throw UserMistake when signature is wrong`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, wrongSignature, 0, false)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with wrong signature`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, wrongSignature, 0, false)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with wrong public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 17
            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    alg = alg,
                    publicKey = wrongPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, true)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `should throw UserMistake when provided with null public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 18
            val nullPublicKey = ByteArray(publicKey.size)
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    alg = alg,
                    publicKey = nullPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, true)
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }

        @Test
        fun `should throw UserMistake when provided with invalid public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 19
            val invalidPublicKey = ByteArray(32) { it.toByte() }
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    alg = alg,
                    publicKey = invalidPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, true)
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }
    }

    @Nested
    inner class Yubikey {
        val objectConverter = ObjectConverter()

        @Test
        fun `ECDSA -7 success`() {
            val alg = -7L
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    alg = alg,
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )

            assertDoesNotThrow {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, signature, 6, false)
            }
        }

        @Test
        fun `ECDSA -7 wrong signature`() {
            val alg = -7L
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    alg = alg,
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, wrongSignature, 6, false)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        @Test
        fun `EdDSA -8 success`() {
            val alg = -8L
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-8.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    alg = alg,
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )

            assertDoesNotThrow {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, signature, 2, false)
            }
        }

        @Test
        fun `EdDSA -8 wrong signature`() {
            val alg = -8L
            val publicKey = extractPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-8.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    alg = alg,
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
            )

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, wrongSignature, 2, false)
            }.isInstanceOf(UserMistake::class.java).messageContains("signature verification failed")
        }

        private fun extractPublicKey(registrationResourcePath: String): ByteArray {
            val registrationResponseJSON = javaClass.getResourceAsStream(registrationResourcePath)!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            return registrationResponse!!.response!!.publicKey!!
        }
    }

    private fun checkAuthentication(rpId: String,
                                    id: ByteArray,
                                    authenticatorData: ByteArray,
                                    clientDataJSON: String,
                                    signature: ByteArray,
                                    signCount: Long,
                                    bs: Boolean) {
        val args = arrayOf(
                gtv(id),
                gtv(authenticatorData),
                gtv(clientDataJSON),
                gtv(signature)
        )
        val opIndex = 1
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, opIndex, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        val op = WebAuthnAuthenticate(WebAuthnConfig(
                listOf(Origin("https://example.org"), Origin("https://webauthn.io")),
                false,
                rpId,
                userPresence = true,
                userVerification = false,
                webAuthnManager = webAuthnManager,
                repository = webAuthnRepository,
        ), opData)
        op.checkCorrectness(ctx)
        op.apply(txCtx)
        verify(webAuthnRepository).updateCredential(txCtx, id, signCount, bs)
    }
}
