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

The client calls `navigator.credentials.create({publicKey: {...}})`, see 
https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialCreationOptions,
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

Call the function `webauthn.require_register(expected_challenge: text)` in the register operation,
and pass in the [challenge](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions#challenge) that the client used. Use the returned `credential_record`'s `id` field to register 
the credential.


## Authentication

### Client

The client calls `navigator.credentials.get({publicKey: {...}})`, see 
https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions,
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

Call the function `webauthn.require_auth(expected_challenge: text)` in operations that should be authenticated,
and pass in the [challenge](https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions#challenge) that the client used. Use the returned `auth_data` to identify the authenticated user.


## DApp example repository

TBD
