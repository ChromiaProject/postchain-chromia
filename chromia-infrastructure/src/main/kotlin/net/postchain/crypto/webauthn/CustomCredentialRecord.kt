package net.postchain.crypto.webauthn

import com.webauthn4j.credential.CoreCredentialRecordImpl
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.client.CollectedClientData

// TODO WebAuthn: Remove this and use CredentialRecordImpl when https://github.com/webauthn4j/webauthn4j/issues/1175 is fixed
class CustomCredentialRecord(
        uvInitialized: Boolean,
        backupEligible: Boolean,
        backupState: Boolean,
        counter: Long,
        attestedCredentialData: AttestedCredentialData,
        val authenticatorTransports: Set<AuthenticatorTransport>,
) : CoreCredentialRecordImpl(
        /*attestationStatement=*/null,
        uvInitialized,
        backupEligible,
        backupState,
        counter,
        attestedCredentialData,
        /*authenticatorExtensions=*/null
), CredentialRecord {
    override fun getClientData(): CollectedClientData? = null
    override fun getTransports(): Set<AuthenticatorTransport?> = authenticatorTransports
}
