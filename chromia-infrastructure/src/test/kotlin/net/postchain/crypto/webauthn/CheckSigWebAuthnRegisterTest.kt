package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CheckSigWebAuthnRegisterTest {

    val validClientDataJSON = """{"type":"webauthn.create","challenge":"dKb","origin":"https://example.org"}"""
    val validAttestationObject = "a363666d74646e6f6e656761747453746d74a068617574684461746158a4bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b559000000008446ccb9ab1db374750b2367ff6f3a1f0020f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4a5010203262001215820afefa16f97ca9b2d23eb86ccb64098d20db90856062eb249c33a9b672f26df61225820930a56b87a2fca66334b03458abf879717c12cc68ed73290af2e2664796b9220".hexStringToByteArray()
    val validId = "41afefa16f97ca9b2d23eb86ccb64098d20db90856062eb249c33a9b672f26df61930a56b87a2fca66334b03458abf879717c12cc68ed73290af2e2664796b9220".hexStringToByteArray()
    val validPublicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnRegister(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("need 6 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("id"),
                gtv("attestationObject"),
                gtv(0),
                gtv(byteArrayOf()),
                gtv(byteArrayOf()),
                gtv(listOf(gtv("usb")))
        )
        val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            CheckSigWebAuthnRegister(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    @Test
    fun `should throw UserMistake when provided invalid clientDataJSON`() {
        assertFailure {
            checkRegistration("example.org", validId, validAttestationObject, "bogus", -7, validPublicKey, listOf("usb"))
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid clientData")
    }

    @Test
    fun `should throw UserMistake when provided invalid attestationObject`() {
        assertFailure {
            checkRegistration("example.org", validId, byteArrayOf(), validClientDataJSON, -7, validPublicKey, listOf("usb"))
        }.isInstanceOf(UserMistake::class.java).messageContains("invalid attestationObject")
    }

    @Test
    fun `should throw UserMistake when crossOrigin does not match`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAttestationObject),
                    gtv("""{"type":"webauthn.create","challenge":"dKb","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(-7),
                    gtv(validPublicKey),
                    gtv(listOf(gtv("usb")))
            )
            val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            CheckSigWebAuthnRegister(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = false, userVerification = false), opData).checkCorrectness()
        }.isInstanceOf(UserMistake::class.java).messageContains("crossOrigin is set but now allowed")
    }

    @Test
    fun `should throw UserMistake when clientData type is wrong`() {
        assertFailure {
            checkRegistration("example.org", validId, validAttestationObject, """{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org"}""", -7, validPublicKey, listOf("usb"))
        }.isInstanceOf(UserMistake::class.java).messageContains("wrong clientData.type")
    }

    @Test
    fun `should throw UserMistake when provided unsupported algorithm`() {
        assertFailure {
            checkRegistration("example.org", validId, validAttestationObject, validClientDataJSON, 0, byteArrayOf(), listOf())
        }.isInstanceOf(UserMistake::class.java).messageContains("unsupported algorithm")
    }

    @Nested
    inner class TestVector {
        /**
         * https://w3c.github.io/webauthn/#sctn-test-vectors-none-es256
         */
        val credentialId = "f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4".hexStringToByteArray()
        val attestationObject = "a363666d74646e6f6e656761747453746d74a068617574684461746158a4bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b559000000008446ccb9ab1db374750b2367ff6f3a1f0020f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4a5010203262001215820afefa16f97ca9b2d23eb86ccb64098d20db90856062eb249c33a9b672f26df61225820930a56b87a2fca66334b03458abf879717c12cc68ed73290af2e2664796b9220".hexStringToByteArray()
        val clientDataJSON = String("7b2274797065223a22776562617574686e2e637265617465222c226368616c6c656e6765223a22414d4d507434557878475453746e63647134313759447742466938767049612d7077386f4f755657345441222c226f726967696e223a2268747470733a2f2f6578616d706c652e6f7267222c2263726f73734f726967696e223a66616c73652c22657874726144617461223a22636c69656e74446174614a534f4e206d617920626520657874656e6465642077697468206164646974696f6e616c206669656c647320696e20746865206675747572652c207375636820617320746869733a20426b5165446a646354427258426941774a544c4535513d3d227d".hexStringToByteArray())
        val publicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

        @Test
        fun `should success when valid`() {
            assertDoesNotThrow { checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -7, publicKey, listOf()) }
        }

        @Test
        fun `should throw UserMistake when origin does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(credentialId),
                        gtv(attestationObject),
                        gtv(clientDataJSON),
                        gtv(-7),
                        gtv(publicKey),
                        gtv(listOf())
                )
                val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                CheckSigWebAuthnRegister(WebAuthnConfig(listOf(Origin("https://bogus.org"), Origin("https://webauthn.io")), true, "example.org", userPresence = false, userVerification = false), opData).checkCorrectness()
            }.isInstanceOf(UserMistake::class.java).messageContains("origin does not match")
        }

        @Test
        fun `should throw UserMistake relying party identifier does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(credentialId),
                        gtv(attestationObject),
                        gtv(clientDataJSON),
                        gtv(-7),
                        gtv(publicKey),
                        gtv(listOf())
                )
                val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                CheckSigWebAuthnRegister(WebAuthnConfig(listOf(), true, "bogus.org", userPresence = false, userVerification = false), opData).checkCorrectness()
            }.isInstanceOf(UserMistake::class.java).messageContains("relying party identifier does not match")
        }

        @Test
        fun `should throw UserMistake when provided with wrong public key`() {
            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            assertFailure {
                checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -7, wrongPublicKey, listOf())
            }.isInstanceOf(UserMistake::class.java).messageContains("public key mismatch")
        }

        @Test
        fun `should throw UserMistake when provided with null public key`() {
            val nullPublicKey = ByteArray(publicKey.size)
            assertFailure {
                checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -7, nullPublicKey, listOf())
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }

        @Test
        fun `should throw UserMistake when provided with invalid public key`() {
            val invalidPublicKey = ByteArray(32) { it.toByte() }
            assertFailure {
                checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -7, invalidPublicKey, listOf())
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }
    }

    @Nested
    inner class Yubikey {
        val objectConverter = ObjectConverter()

        @Test
        fun `ECDSA -7 success`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-7.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            assertDoesNotThrow {
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -7, publicKey, transports)
            }
        }

        @Test
        fun `ECDSA -7 wrong alg`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-7.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            assertFailure {
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -8, publicKey, transports)
            }.isInstanceOf(UserMistake::class.java).messageContains("invalid public key")
        }

        @Test
        fun `ECDSA -7 wrong public key`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-7.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            assertFailure {
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -7, wrongPublicKey, transports)
            }.isInstanceOf(UserMistake::class.java).messageContains("public key mismatch")
        }

        @Test
        fun `ECDSA -7 wrong id`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-7.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            val wrongId = id.clone()
            wrongId[10] = 17
            assertFailure {
                checkRegistration("webauthn.io", wrongId, attestationObject, clientDataJSON, -7, publicKey, transports)
            }.isInstanceOf(UserMistake::class.java).messageContains("credentialId mismatch")
        }

        @Test
        fun `EdDSA -8 success`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-8.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            assertDoesNotThrow {
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -8, publicKey, transports)
            }
        }

        @Test
        fun `EdDSA -8 wrong public key`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-8.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            val wrongPublicKey = publicKey.clone()
            wrongPublicKey[30] = 17
            assertFailure {
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -8, wrongPublicKey, transports)
            }.isInstanceOf(UserMistake::class.java).messageContains("public key mismatch")
        }

        @Test
        fun `EdDSA -8 wrong id`() {
            val registrationResponseJSON = javaClass.getResourceAsStream("/net/postchain/crypto/webauthn/registration-8.json")!!
            val registrationResponse = objectConverter.jsonConverter.readValue<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>(registrationResponseJSON, object : TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse?, RegistrationExtensionClientOutput?>?>() {})
            val id = registrationResponse!!.rawId!!
            val attestationObject = registrationResponse.response!!.attestationObject
            val clientDataJSON = String(registrationResponse.response!!.clientDataJSON)
            val publicKey = registrationResponse.response!!.publicKey!!
            val transports = registrationResponse.response!!.transports.map { it.value }

            val wrongId = id.clone()
            wrongId[10] = 17
            assertFailure {
                checkRegistration("webauthn.io", wrongId, attestationObject, clientDataJSON, -8, publicKey, transports)
            }.isInstanceOf(UserMistake::class.java).messageContains("credentialId mismatch")
        }
    }

    private fun checkRegistration(rpId: String, id: ByteArray, attestationObject: ByteArray, clientDataJSON: String, alg: Long, publicKey: ByteArray, transports: List<String>) {
        val args = arrayOf(
                gtv(id),
                gtv(attestationObject),
                gtv(clientDataJSON),
                gtv(alg),
                gtv(publicKey),
                gtv(transports.map { gtv(it) })
        )
        val opData = ExtOpData(CheckSigWebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        CheckSigWebAuthnRegister(WebAuthnConfig(
                listOf(Origin("https://example.org"), Origin("https://webauthn.io")),
                false,
                rpId,
                userPresence = true,
                userVerification = false
        ), opData).checkCorrectness()
    }
}
