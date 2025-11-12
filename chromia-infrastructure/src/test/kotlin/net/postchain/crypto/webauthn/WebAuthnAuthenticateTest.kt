package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.CollectedClientDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.KeyProtectionType
import com.webauthn4j.data.MatcherProtectionType
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.UserVerificationMethod
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.client.ClientDataType
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.TokenBinding
import com.webauthn4j.data.client.TokenBindingStatus
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.data.extension.UvmEntries
import com.webauthn4j.data.extension.UvmEntry
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs
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
import net.postchain.crypto.sha256Digest
import net.postchain.crypto.webauthn.WebAuthnGTXModuleFactory.Companion.createWebAuthnManager
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
import kotlin.experimental.or

class WebAuthnAuthenticateTest {

    val objectConverter = ObjectConverter()
    val collectedClientDataConverter = CollectedClientDataConverter(objectConverter)

    val validId = "f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4".hexStringToByteArray()
    val validClientDataJSON = """{"type":"webauthn.get","challenge":"OcDnUhQXulTUPo3JUXT0I97pvzzYBP9tZchXyav01Ag","origin":"https://example.org","crossOrigin":false}"""
    val validAuthenticatorData = "bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b51900000000".hexStringToByteArray()
    val validSignature = "3046022100f50a4e2e4409249c4a853ba361282f09841df4dd4547a13a87780218deffcd380221008480ac0f0b93538174f575bf11a1dd5d78c6e486013f937295ea13653e331e87".hexStringToByteArray()
    val validAAGUID = "08987058cadc4b81b6e130de50dcbe96".hexStringToByteArray()
    val validPublicKey = "A5010203262001215820AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61225820930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()
    val existingChallenge = ByteArray(16) { 1 }
    val duplicateClientDataJSON = collectedClientDataConverter.convertToBytes(
            CollectedClientData(ClientDataType.WEBAUTHN_GET, DefaultChallenge(existingChallenge), Origin("https://example.org"), null)
    ).toString(Charsets.UTF_8)
    val longOrigin = Origin("https://${"x".repeat(250)}.com")

    val webAuthnManager = createWebAuthnManager(objectConverter, false).first
    val attestationObjectConverter = AttestationObjectConverter(objectConverter)
    val webAuthnRepository: WebAuthnRepository = mock {
        on { fetchCredential(any(), eq(validId)) } doReturn CredentialData(
                id = validId.wrap(),
                aaguid = validAAGUID.wrap(),
                publicKey = validPublicKey.wrap(),
                signCount = 1,
                transports = "usb,nfc",
                uvInitialized = false,
                backupEligible = true,
                backupState = true,
                suspiciousSignCountPresented = null,
                suspiciousSignCountStored = null,
        )
        on { challengeExists(any(), eq(existingChallenge)) } doReturn true
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
            WebAuthnAuthenticate(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("need 4 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("message"),
                gtv("{}"),
                gtv(0),
                gtv(validSignature),
        )
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            WebAuthnAuthenticate(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    @Test
    fun `should throw UserMistake when provided invalid clientDataJSON`() {
        assertFailure {
            checkAuthentication("example.org", validId, validAuthenticatorData, "bogus", validSignature, 0, uv = false, bs = false)
        }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
    }

    @Test
    fun `should throw UserMistake when provided duplicate challenge`() {
        assertFailure {
            checkAuthentication("example.org", validId, validAuthenticatorData, duplicateClientDataJSON, validSignature, 0, uv = false, bs = false)
        }.isInstanceOf(UserMistake::class.java).messageContains("challenge is not unique")
    }

    @Test
    fun `should throw UserMistake when provided invalid authenticatorData`() {
        assertFailure {
            checkAuthentication("example.org", validId, byteArrayOf(), validClientDataJSON, validSignature, 0, uv = false, bs = false)
        }.isInstanceOf(UserMistake::class.java).messageContains("provided data does not have proper byte layout")
    }

    @Test
    fun `should throw UserMistake when provided authenticatorData is too large`() {
        assertFailure {
            checkAuthentication("example.org", validId, validAuthenticatorData + ByteArray(16), validClientDataJSON, validSignature, 0, uv = false, bs = false)
        }.isInstanceOf(UserMistake::class.java).messageContains("provided data does not have proper byte layout")
    }

    @Test
    fun `should throw UserMistake when crossOrigin is set but not allowed`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAuthenticatorData),
                    gtv("""{"type":"webauthn.get","challenge":"0000000000000000000000000000000000000000000000","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(validSignature)
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = false, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("doesn't match any of the preconfigured server topOrigin")
    }

    @Test
    fun `should accept crossOrigin when it is allowed`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAuthenticatorData),
                    gtv("""{"type":"webauthn.get","challenge":"0000000000000000000000000000000000000000000000","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(validSignature)
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), true, "example.org", userPresence = false, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid") // if we get there, the crossOrigin check is already done
    }

    @Test
    fun `should throw UserMistake when user is not verified`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAuthenticatorData),
                    gtv(validClientDataJSON),
                    gtv(validSignature)
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = true, userVerification = true, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Verifier is configured to check user verified, but UV flag in authenticatorData is not set")
    }

    @Test
    fun `should throw UserMistake when user is not registered`() {
        val invalidId = ByteArray(16)
        assertFailure {
            val args = arrayOf(
                    gtv(invalidId),
                    gtv(validAuthenticatorData),
                    gtv(validClientDataJSON),
                    gtv(validSignature)
            )
            val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = true, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("credential with id ${invalidId.toHex()} not registered")
    }

    @Nested
    inner class SizeLimits {
        @Test
        fun `should throw UserMistake when authenticatorData has superfluous data at end`() {
            assertFailure {
                checkAuthentication("example.org", validId, validAuthenticatorData + ByteArray(16), validClientDataJSON, validSignature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("provided data does not have proper byte layout")
        }

        @Test
        fun `should throw UserMistake when authenticatorData is too large`() {
            val authenticatorData = AuthenticatorData(sha256Digest("example.org".toByteArray(Charsets.UTF_8)),
                    AuthenticatorData.BIT_UP or AuthenticatorData.BIT_BE or AuthenticatorData.BIT_ED, 17, null,
                    AuthenticationExtensionsAuthenticatorOutputs.BuilderForAuthentication()
                            .setUvm(UvmEntries(List(256) {
                                UvmEntry(UserVerificationMethod.PATTERN_EXTERNAL, KeyProtectionType.REMOTE_HANDLE, MatcherProtectionType.SOFTWARE)
                            }))
                            .setHMACGetSecret(ByteArray(64) { 1 })
                            .build()).let { AuthenticatorDataConverter(objectConverter).convert(it) }
            assertFailure {
                checkAuthentication("example.org", validId, authenticatorData, validClientDataJSON, validSignature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("authenticatorData is too large")
        }

        @Test
        fun `should throw UserMistake when clientData is too large`() {
            val clientDataJSON = CollectedClientData(
                    ClientDataType.WEBAUTHN_GET,
                    DefaultChallenge(ByteArray(64) { it.toByte() }),
                    longOrigin,
                    false,
                    TokenBinding(TokenBindingStatus.PRESENT, "x".repeat(64))
            ).let { CollectedClientDataConverter(objectConverter).convertToBytes(it) }.toString(Charsets.UTF_8)
            assertFailure {
                checkAuthentication("example.org", validId, validAuthenticatorData, clientDataJSON, validSignature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("clientData is too large")
        }

        @Test
        fun `should throw UserMistake when signature has superfluous data at end`() {
            assertFailure {
                checkAuthentication("example.org", validId, validAuthenticatorData, validClientDataJSON, validSignature + ByteArray(16), 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }
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
        val aaguid = "8446ccb9ab1db374750b2367ff6f3a1f".hexStringToByteArray()
        val publicKey = "A5010203262001215820AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61225820930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

        @BeforeEach
        fun setup() {
            whenever(webAuthnRepository.fetchCredential(any(), eq(credentialId))) doReturn CredentialData(
                    id = credentialId.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 0,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )
        }

        @Test
        fun `should success with valid signature`() {
            assertDoesNotThrow {
                checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, signature, 0, uv = false, bs = true)
            }
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
                WebAuthnAuthenticate(WebAuthnConfig(listOf(Origin("https://bogus.org"), Origin("https://webauthn.io")), true, "example.org", userPresence = false, userVerification = false, objectConverter, webAuthnManager, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
            }.isInstanceOf(UserMistake::class.java).messageContains("The collectedClientData origin 'https://example.org' doesn't match any of the preconfigured server origin")
        }

        @Test
        fun `should throw UserMistake relying party identifier does not match`() {
            assertFailure {
                checkAuthentication("bogus.org", credentialId, authenticatorData, clientDataJSON, signature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("rpIdHash doesn't match the hash of preconfigured rpId")
        }

        @Test
        fun `should throw UserMistake when signature is wrong`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, wrongSignature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }

        @Test
        fun `should throw UserMistake when provided with wrong signature`() {
            val wrongSignature = signature.clone()
            wrongSignature[20] = 17
            assertFailure {
                checkAuthentication("example.org", credentialId, authenticatorData, clientDataJSON, wrongSignature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }

        @Test
        fun `should throw UserMistake when provided with wrong public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 17
            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = wrongPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }

        @Test
        fun `should throw UserMistake when provided with null public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 18
            val nullPublicKey = ByteArray(publicKey.size)
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = nullPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
        }

        @Test
        fun `should throw UserMistake when provided with invalid public key`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[30] = 19
            val invalidPublicKey = ByteArray(32) { it.toByte() }
            whenever(webAuthnRepository.fetchCredential(any(), eq(wrongCredentialId))) doReturn CredentialData(
                    id = wrongCredentialId.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = invalidPublicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = false,
                    backupEligible = true,
                    backupState = true,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )
            assertFailure {
                checkAuthentication("example.org", wrongCredentialId, authenticatorData, clientDataJSON, signature, 0, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
        }
    }

    @Nested
    inner class Yubikey {
        @Test
        fun `ECDSA -7 success`() {
            val (aaguid, publicKey) = extractAaguidAndPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = true,
                    backupEligible = false,
                    backupState = false,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )

            assertDoesNotThrow {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, signature, 6, uv = true, bs = false)
            }
        }

        @Test
        fun `ECDSA -7 suspicious sign count`() {
            val (aaguid, publicKey) = extractAaguidAndPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 10,
                    transports = "usb,nfc",
                    uvInitialized = true,
                    backupEligible = false,
                    backupState = false,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )

            assertDoesNotThrow {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, signature, 6, uv = true, bs = false,
                        suspiciousSignCountPresented = 6, suspiciousSignCountStored = 10)
            }
        }

        @Test
        fun `ECDSA -7 wrong signature`() {
            val (aaguid, publicKey) = extractAaguidAndPublicKey("/net/postchain/crypto/webauthn/registration-7.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-7.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = true,
                    backupEligible = false,
                    backupState = false,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, wrongSignature, 6, uv = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }

        @Test
        fun `EdDSA -8 success`() {
            val (aaguid, publicKey) = extractAaguidAndPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-8.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = true,
                    backupEligible = false,
                    backupState = false,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )

            assertDoesNotThrow {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, signature, 2, uv = true, bs = false)
            }
        }

        @Test
        fun `EdDSA -8 wrong signature`() {
            val (aaguid, publicKey) = extractAaguidAndPublicKey("/net/postchain/crypto/webauthn/registration-8.json")
            val authenticationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/authentication-8.json")!!
            val authenticationResponse = objectConverter.jsonConverter.readValue(authenticationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse?, AuthenticationExtensionClientOutput?>?>() {})
            val id = authenticationResponse!!.rawId!!
            val authenticatorData = authenticationResponse.response!!.authenticatorData
            val clientDataJSON = String(authenticationResponse.response!!.clientDataJSON)
            val signature = authenticationResponse.response!!.signature

            whenever(webAuthnRepository.fetchCredential(any(), eq(id))) doReturn CredentialData(
                    id = id.wrap(),
                    aaguid = aaguid.wrap(),
                    publicKey = publicKey.wrap(),
                    signCount = 1,
                    transports = "usb,nfc",
                    uvInitialized = true,
                    backupEligible = false,
                    backupState = false,
                    suspiciousSignCountPresented = null,
                    suspiciousSignCountStored = null,
            )

            val wrongSignature = signature.clone()
            wrongSignature[30] = 17
            assertFailure {
                checkAuthentication("webauthn.io", id, authenticatorData, clientDataJSON, wrongSignature, 2, uv = true, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("Assertion signature is not valid")
        }

        private fun extractAaguidAndPublicKey(registrationResourcePath: String): Pair<ByteArray, ByteArray> {
            val registrationResponseJSON = javaClass.getResourceAsStream(registrationResourcePath)!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val decodedAttestationObject = attestationObjectConverter.convert(registrationResponse!!.response!!.attestationObject)!!
            val aaguid = decodedAttestationObject.authenticatorData.attestedCredentialData!!.aaguid.bytes!!
            val coseKey = decodedAttestationObject.authenticatorData.attestedCredentialData!!.coseKey
            return aaguid to objectConverter.cborConverter.writeValueAsBytes(coseKey)
        }
    }

    private fun checkAuthentication(
            rpId: String,
            id: ByteArray,
            authenticatorData: ByteArray,
            clientDataJSON: String,
            signature: ByteArray,
            signCount: Long,
            uv: Boolean,
            bs: Boolean,
            suspiciousSignCountPresented: Long? = null,
            suspiciousSignCountStored: Long? = null,
    ) {
        val args = arrayOf(
                gtv(id),
                gtv(authenticatorData),
                gtv(clientDataJSON),
                gtv(signature)
        )
        val opIndex = 1
        val opData = ExtOpData(WebAuthnAuthenticate.OP_NAME, opIndex, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        val op = WebAuthnAuthenticate(WebAuthnConfig(
                listOf(Origin("https://example.org"), Origin("https://webauthn.io"), longOrigin),
                false,
                rpId,
                userPresence = true,
                userVerification = false,
                objectConverter = objectConverter,
                strictWebAuthnManager = webAuthnManager,
                nonStrictWebAuthnManager = webAuthnManager,
                repository = webAuthnRepository,
        ), opData)
        op.checkCorrectness(ctx)
        op.apply(txCtx)

        val challenge = collectedClientDataConverter.convert(clientDataJSON.toByteArray(Charsets.UTF_8))!!.challenge.value
        verify(webAuthnRepository).persistChallenge(txCtx, challenge)

        verify(webAuthnRepository).updateCredential(txCtx, id, signCount, uvInitialized = uv, backupState = bs,
                suspiciousSignCountPresented = suspiciousSignCountPresented, suspiciousSignCountStored = suspiciousSignCountStored)
    }
}
