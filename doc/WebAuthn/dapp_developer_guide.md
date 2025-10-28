# DApp developer guide

This is documentation for DApp developers that want to use [Web Authentication](https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API).


## Configuration
You will need to include the WebAuthn GTX module in your blockchain configuration, configure it and install the Rell library:

```yaml
config:
  gtx:
    modules:
      - "net.postchain.crypto.webauthn.WebAuthnGTXModuleFactory"
  webauthn:
    allowed-origins: # https://w3c.github.io/webauthn/#dom-collectedclientdata-origin
      - "https://my-dapp.somewhere.com/" 
    allow-cross-origin: false # https://w3c.github.io/webauthn/#dom-collectedclientdata-crossorigin 
    relying-party-identifier: "my-dapp.somewhere.com" # https://w3c.github.io/webauthn/#rp-id
    user-presence: true # https://w3c.github.io/webauthn/#concept-user-present
    user-verification: false # https://w3c.github.io/webauthn/#user-verification
    verify-attestation: false # https://w3c.github.io/webauthn/#reg-ceremony-verify-attestation
libs:
  com.chromia.webauthn:
    version: ${VERSION}
```


## Registration

### Client

The client calls 
```javascript
navigator.credentials.create({publicKey: {
    challenge: uniqueChallenge, // this needs to be unique and at least 16 bytes
    rp: {
      name: "My DApp", // user readable name of the DApp 
      id: "my-dapp.somewhere.com", // this needs to match "relying-party-identifier" in blockchain config
    },
    pubKeyCredParams: [
      { alg: -7, type: 'public-key' }, // ES256
      { alg: -8, type: 'public-key' }, // EdDSA
    ],
    authenticatorSelection: {
      residentKey: 'required',
    },        
    attestation: 'direct', // specify 'none' if "verify-attestation" in blockchain config is false, 'direct' if it's true
    // other properties here    
}});
```
see https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialCreationOptions,
and then includes this operation before the registration operation in the transaction:
```
operation gtxc.webauthn_register(
    // https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/rawId
    id: byte_array,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/attestationObject
    attestation_object: byte_array,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAttestationResponse
    client_data_json: text,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAttestationResponse/getTransports
    transports: list<text>,
)
```

Note that the `gtxc.webauthn_register` operation is provided by the GTX module and not implemented in Rell.
It will store the public key and other information in a credential record. 

### Rell

Call the function `webauthn.require_register()` in the register operation. Use the returned `credential_record`'s
`id` field to register the credential.


## Authentication

### Client

The client calls 
```javascript
navigator.credentials.get({publicKey: {
    challenge: uniqueChallenge, // this needs to be unique and at least 16 bytes
    rpId: "my-dapp.somewhere.com", // this needs to match "relying-party-identifier" in blockchain config
    // other properties here    
}});
```
see https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions,
and then includes this operation before the authenticated operation in the transaction:
```
operation gtxc.webauthn_authenticate(
    // https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredential/rawId
    id: byte_array,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/authenticatorData from AuthenticatorAssertionResponse
    authenticator_data: byte_array,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorResponse/clientDataJSON from AuthenticatorAssertionResponse
    client_data_json: text,

    // https://developer.mozilla.org/en-US/docs/Web/API/AuthenticatorAssertionResponse/signature from AuthenticatorAssertionResponse
    signature: byte_array,
)
```

Note that the `gtxc.webauthn_authenticate` operation is provided by the GTX module and not implemented in Rell. It
will look up the credential record stored by previous `gtxc.webauthn_register` operation based on the `id`. 

### Rell

Call the function `webauthn.require_auth()` in operations that should be authenticated. Use the returned `auth_data`'s
`id` field to identify the authenticated user.

## Queries

The GTX module exposes a query which can be used to look up registered credentials:
```
/** https://w3c.github.io/webauthn/#credential-record */
struct credential_record {
    /** the transaction where the credential was registreded */ 
    txRid: byte_array;
    
    /** index of the gtxc.webauthn_register operation in that transaction */ 
    opIndex: integer;

    id: byte_array;
    
    /** AAGUID: https://w3c.github.io/webauthn/#aaguid */
    aaguid: byte_array;
    
    /** public key in COSE format: https://datatracker.ietf.org/doc/html/rfc9052#section-7 */
    publicKey: byte_array;
    
    signCount: integer;
    
    /** transports as comma separated list */
    transports: text; 
    
    uvInitialized: boolean;

    backupEligible: boolean;
    
    backupState: boolean;

    /** set to the presented signature counter value if a mismatch was detected, null otherwise, see https://w3c.github.io/webauthn/#sctn-sign-counter */     
    suspiciousSignCountPresented: integer?;
     
    /** set to the stored signature counter value if a mismatch was detected, null otherwise, see https://w3c.github.io/webauthn/#sctn-sign-counter */     
    suspiciousSignCountStored: integer?; 
}

query gtxc.webauthn_get_credential(id: byte_array): credential_record?
```

## DApp example repository

https://bitbucket.org/chromawallet/passkey-demo/src/main/
