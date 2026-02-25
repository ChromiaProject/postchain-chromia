package net.postchain.crypto.webauthn.webauthn4j

import com.webauthn4j.credential.CoreCredentialRecordImpl
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.client.CollectedClientData

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

    var suspiciousSignCount: Pair<Long, Long>? = null
}
