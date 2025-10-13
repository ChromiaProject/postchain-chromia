# DApp developer guide

This is documentation for dApp developers that want to use [Web Authentication](https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API).


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
  webauthn:
    registry: https://gitlab.com/chromaway/postchain-chromia
    path: chromia-infrastructure/rell/src/lib/webauthn
    tagOrBranch: ${VERSION}
    rid: x"${LIBRARY_RID}"
    insecure: false
```


## Registration

### Client

The client calls 
```javascript
navigator.credentials.create({publicKey: {
    challenge: uniqueChallenge, // this needs to be unique
    rp: {
      name: "My DApp", // user readable name of the DApp 
      id: "my-dapp.somewhere.com", // this needs to match "relying-party-identifier" in blockchain config
    },
    pubKeyCredParams: [
      { alg: -7, type: 'public-key' }, // ES256
      { alg: -8, type: 'public-key' }, // EdDSA
    ],
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
    challenge: uniqueChallenge, // this needs to be unique
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


## DApp example repository

TBD
