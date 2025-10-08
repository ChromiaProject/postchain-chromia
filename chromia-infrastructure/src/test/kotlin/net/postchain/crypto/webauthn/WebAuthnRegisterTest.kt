package net.postchain.crypto.webauthn

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.fasterxml.jackson.core.type.TypeReference
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.wrap
import net.postchain.core.MockEContext
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WebAuthnRegisterTest {

    val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    val webAuthnRepository: WebAuthnRepository = mock()
    val ctx = MockEContext(0)
    val blockCtx = BaseBlockEContext(ctx, 0, 0, 0, mapOf()) { _, _, _ -> }
    val txCtx = BaseTxEContext(blockCtx, 0, TestTransaction(0))

    val validClientDataJSON = """{"type":"webauthn.create","challenge":"dKb","origin":"https://example.org"}"""
    val validAttestationObject = "a363666d74646e6f6e656761747453746d74a068617574684461746158a4bfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b559000000008446ccb9ab1db374750b2367ff6f3a1f0020f91f391db4c9b2fde0ea70189cba3fb63f579ba6122b33ad94ff3ec330084be4a5010203262001215820afefa16f97ca9b2d23eb86ccb64098d20db90856062eb249c33a9b672f26df61225820930a56b87a2fca66334b03458abf879717c12cc68ed73290af2e2664796b9220".hexStringToByteArray()
    val validId = "41afefa16f97ca9b2d23eb86ccb64098d20db90856062eb249c33a9b672f26df61930a56b87a2fca66334b03458abf879717c12cc68ed73290af2e2664796b9220".hexStringToByteArray()
    val validPublicKey = "3059301306072A8648CE3D020106082A8648CE3D03010703420004AFEFA16F97CA9B2D23EB86CCB64098D20DB90856062EB249C33A9B672F26DF61930A56B87A2FCA66334B03458ABF879717C12CC68ED73290AF2E2664796B9220".hexStringToByteArray()

    @Test
    fun `should throw UserMistake when provided with incorrect argument counts`() {
        val args = arrayOf(
                gtv("OnlyOneArgument")
        )
        val opData = ExtOpData(WebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            WebAuthnRegister(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("need 4 args")
    }

    @Test
    fun `should throw UserMistake when provided with incorrect argument types`() {
        val args = arrayOf(
                gtv("id"),
                gtv("attestationObject"),
                gtv(byteArrayOf()),
                gtv(listOf(gtv("usb")))
        )
        val opData = ExtOpData(WebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())

        assertFailure {
            WebAuthnRegister(WebAuthnConfig(listOf(), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Can't create ByteArray from string")
    }

    @Test
    fun `should throw UserMistake when provided invalid clientDataJSON`() {
        assertFailure {
            checkRegistration("example.org", validId, validAttestationObject, "bogus", -7, validPublicKey, listOf("usb"), 0, uv = false, be = true, bs = true)
        }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
    }

    @Test
    fun `should throw UserMistake when provided invalid attestationObject`() {
        assertFailure {
            checkRegistration("example.org", validId, byteArrayOf(), validClientDataJSON, -7, validPublicKey, listOf("usb"), 0, uv = false, be = true, bs = true)
        }.isInstanceOf(UserMistake::class.java).messageContains("Input data does not match expected form")
    }

    @Test
    fun `should throw UserMistake when crossOrigin does not match`() {
        assertFailure {
            val args = arrayOf(
                    gtv(validId),
                    gtv(validAttestationObject),
                    gtv("""{"type":"webauthn.create","challenge":"dKb","origin":"https://example.org","crossOrigin":true}"""),
                    gtv(listOf(gtv("usb")))
            )
            val opData = ExtOpData(WebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
            WebAuthnRegister(WebAuthnConfig(listOf(Origin("https://example.org"), Origin("https://webauthn.io")), false, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
        }.isInstanceOf(UserMistake::class.java).messageContains("Cross-origin request is prohibited")
    }

    @Test
    fun `should throw UserMistake when clientData type is wrong`() {
        assertFailure {
            checkRegistration("example.org", validId, validAttestationObject, """{"type":"webauthn.get","challenge":"dKb","origin":"https://example.org"}""", -7, validPublicKey, listOf("usb"), 0, uv = false, be = true, bs = true)
        }.isInstanceOf(UserMistake::class.java).messageContains("ClientData.type must be 'create' on registration")
    }

    @Test
    fun `should throw UserMistake when key algorithm is unsupported`() {
        // RS-256 (-257)
        // https://w3c.github.io/webauthn/#sctn-test-vectors-packed-rs256
        val credentialId = "992a18acc83f67533600c1138a4b4c4bd236de13629cf025ed17cb00b00b74df".hexStringToByteArray()
        val attestationObject = "a363666d74667061636b65646761747453746d74a363616c672663736967584730450221008b8c5c6ea8c142c032e0be69e1353d44461c5c9109941cdda951b976eb95b6b302204d52f406c19e254b3ff9589bd18070fb055ac8db12fdd0a6734bea9d7168e900637835638159022630820222308201c7a00302010202101f6fb7a5ece81b45896b983a995da5f3300a06082a8648ce3d0403023062311e301c06035504030c15576562417574686e207465737420766563746f7273310c300a060355040a0c0357334331253023060355040b0c1c41757468656e74696361746f72204174746573746174696f6e204341310b30090603550406130241413020170d3234303130313030303030305a180f33303234303130313030303030305a305f311e301c06035504030c15576562417574686e207465737420766563746f7273310c300a060355040a0c0357334331223020060355040b0c1941757468656e74696361746f72204174746573746174696f6e310b30090603550406130241413059301306072a8648ce3d020106082a8648ce3d03010703420004b7b36b7542a11120b443c794d0c99fdc25a06b76586413d81e086163ef6fe147a557afc34e2861d9057d6d465d4705a0310550bdeeb5f35ee35b9425ab859981a360305e300c0603551d130101ff04023000300e0603551d0f0101ff040403020780301d0603551d0e04160414fb37b647bccfb9e54d989eaaacc1633868703fb3301f0603551d2304183016801445aff715b0dd786741fee996ebc16547a3931b1e300a06082a8648ce3d0403020349003046022100b86bc129d92afca7d9869a39f70f139a305b4073a39eb654d81424bed5757d91022100cf9f7c60cab7c4a7d3e7f0020f281a93d4fd0a9f95121b989f56932a68885fba68617574684461746159021bbfabc37432958b063360d3ad6461c9c4735ae7f8edd46592a5e0f01452b2e4b55d00000000428f8878298b9862a36ad8c7527bfef20020992a18acc83f67533600c1138a4b4c4bd236de13629cf025ed17cb00b00b74dfa4010303390100205901b403fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000012143010001".hexStringToByteArray()
        val clientDataJSON = String("7b2274797065223a22776562617574686e2e637265617465222c226368616c6c656e6765223a2276716a776477414a76566679774e3976367039304f69666b7468752d6b6a79474c48717465705f49354b59222c226f726967696e223a2268747470733a2f2f6578616d706c652e6f7267222c2263726f73734f726967696e223a66616c73657d".hexStringToByteArray())

        assertFailure {
            checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -257, ByteArray(0), listOf("usb"), 0, uv = false, be = true, bs = true)
        }.isInstanceOf(UserMistake::class.java).messageContains("alg not listed in options.pubKeyCredParams is used")
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
            assertDoesNotThrow { checkRegistration("example.org", credentialId, attestationObject, clientDataJSON, -7, publicKey, listOf(), 0, uv = false, be = true, bs = true) }
        }

        @Test
        fun `should throw UserMistake when origin does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(credentialId),
                        gtv(attestationObject),
                        gtv(clientDataJSON),
                        gtv(listOf())
                )
                val opData = ExtOpData(WebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                WebAuthnRegister(WebAuthnConfig(listOf(Origin("https://bogus.org"), Origin("https://webauthn.io")), true, "example.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
            }.isInstanceOf(UserMistake::class.java).messageContains("The collectedClientData 'https://example.org' origin doesn't match")
        }

        @Test
        fun `should throw UserMistake relying party identifier does not match`() {
            assertFailure {
                val args = arrayOf(
                        gtv(credentialId),
                        gtv(attestationObject),
                        gtv(clientDataJSON),
                        gtv(listOf())
                )
                val opData = ExtOpData(WebAuthnRegister.OP_NAME, 0, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
                WebAuthnRegister(WebAuthnConfig(listOf(Origin("https://example.org")), true, "bogus.org", userPresence = false, userVerification = false, webAuthnManager, webAuthnRepository), opData).checkCorrectness(ctx)
            }.isInstanceOf(UserMistake::class.java).messageContains("rpIdHash doesn't match the hash of preconfigured rpId")
        }

        @Test
        fun `should throw UserMistake when provided with wrong id`() {
            val wrongCredentialId = credentialId.clone()
            wrongCredentialId[20] = 17
            assertFailure {
                checkRegistration("example.org", wrongCredentialId, attestationObject, clientDataJSON, -7, publicKey, listOf(), 0, uv = false, be = true, bs = true)
            }.isInstanceOf(UserMistake::class.java).messageContains("credentialId mismatch")
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
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -7, publicKey, transports, 2, uv = true, be = false, bs = false)
            }
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
                checkRegistration("webauthn.io", wrongId, attestationObject, clientDataJSON, -7, publicKey, transports, 2, uv = true, be = false, bs = false)
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
                checkRegistration("webauthn.io", id, attestationObject, clientDataJSON, -8, publicKey, transports, 1, uv = true, be = false, bs = false)
            }
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
                checkRegistration("webauthn.io", wrongId, attestationObject, clientDataJSON, -8, publicKey, transports, 1, uv = true, be = false, bs = false)
            }.isInstanceOf(UserMistake::class.java).messageContains("credentialId mismatch")
        }
    }

    private fun checkRegistration(rpId: String,
                                  id: ByteArray,
                                  attestationObject: ByteArray,
                                  clientDataJSON: String,
                                  alg: Long,
                                  publicKey: ByteArray,
                                  transports: List<String>,
                                  signCount: Long,
                                  uv: Boolean,
                                  be: Boolean,
                                  bs: Boolean) {
        val args = arrayOf(
                gtv(id),
                gtv(attestationObject),
                gtv(clientDataJSON),
                gtv(transports.map { gtv(it) })
        )
        val opIndex = 1
        val opData = ExtOpData(WebAuthnRegister.OP_NAME, opIndex, args, BlockchainRid.ZERO_RID, arrayOf(), arrayOf())
        val op = WebAuthnRegister(WebAuthnConfig(
                listOf(Origin("https://example.org"), Origin("https://webauthn.io")),
                false,
                rpId,
                userPresence = true,
                userVerification = uv,
                webAuthnManager = webAuthnManager,
                repository = webAuthnRepository,
        ), opData)
        op.checkCorrectness(ctx)
        op.apply(txCtx)
        verify(webAuthnRepository).persistCredential(txCtx, opIndex, CredentialData(
                id = id.wrap(),
                alg = alg,
                publicKey = publicKey.wrap(),
                signCount = signCount,
                transports = transports.joinToString(separator = ","),
                uvInitialized = uv,
                backupEligible = be,
                backupState = bs,
        ))
    }
}
