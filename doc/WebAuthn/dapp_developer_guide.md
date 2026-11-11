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

> **Security: bind the assertion to the operation.**
> The node checks that the challenge is 16–64 bytes and has not been used before, but it does not check what the
> challenge was derived from. Whoever holds an unused assertion can put it in front of *any* operation, so treat an
> assertion like a one-time bearer token unless the operation checks the challenge (see Rell below):
> - Derive the challenge from the operation it authorizes. Give the operation a nonce argument, so that the user can
>   make the same call twice, and compute the challenge as the GTV hash of
>   `[blockchain_rid, operation_name, operation_args]`, with the operation name as sent in the transaction (postchain-client `gtvHash`, with the chain's merkle hash version).
> - One `gtxc.webauthn_authenticate` operation covers every later operation in the transaction that calls
>   `require_auth()`, until the next one. With a bound challenge, each authenticated operation needs its own assertion.
> - Never send an assertion anywhere except straight into the transaction it was created for, and never log it.

### Rell

Call the function `webauthn.require_auth()` in operations that should be authenticated. Use the returned `auth_data`'s
`id` field to identify the authenticated user.

To bind the assertion to the operation, call `webauthn.require_auth_for_operation()` instead. It fails unless the
challenge equals `webauthn.operation_challenge()`, the hash of the blockchain RID and the current operation's name and
arguments:

```
operation transfer(to: byte_array, amount: integer, nonce: integer) {
    val auth_data = webauthn.require_auth_for_operation();
    // ...
}
```

If you derive the challenge some other way, compute it in the operation and call
`webauthn.require_auth_for(expected_challenge)`. The challenge in `clientDataJSON` is base64url without padding, while
Rell's `byte_array.from_base64()` and `to_base64()` use standard base64; `webauthn.base64url()` does the conversion.

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
