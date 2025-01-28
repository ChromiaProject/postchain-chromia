# Client Migration Guide for Merkle Hash version 2

This guide is intended for developers of Postchain client software.

## Background

The initial implementation of the GTV merkle hash calculation contained a bug which leads to potential collisions.

The problem is that the hash calculation of a GTV array that holds a single element which is also a collection, either
another array or a dictionary, will collapse the structure to keep only the outer array. This leads to the hash being
the same for e.g. `["a"]` and `[["a"]]`.

## Patch

The patch in the Kotlin implementation is very small and can be viewed in this commit:

https://gitlab.com/chromaway/core/postchain/-/blob/dev/postchain-gtv/src/main/kotlin/net/postchain/gtv/merkle/factory/GtvBinaryTreeFactoryArray.kt?ref_type=heads#L31-33

## Detecting the version a chain is using

The old buggy version is referred to as version 1 and the patched as version 2.

### Configuration

Merkle hash version is configured in blockchain configuration:

```
config:
    features:
        merkle_hash_version: 2
```

If the configuration is not there, the chain is using version 1.

### Block header

Hash version is included in the `extra` part of the block header under the key `merkle_hash_version`. If it is not in
the header, the chain is using version 1.

## Manual or automated detection of hash version?

It is probably wise to offer a way to just configure the hash version that the client should use manually.
Most likely it will be version 2 for all blockchains on mainnet after some time.

As mentioned above it is also possible to auto-detect the current or historical version by either inspecting the
configuration or block header at the height of interest.

## ICCF

For cross-chain features like ICCF we have all the material to auto-detect hash version on the source chain since
we have the block header in the proof. So we can use that information to verify the source tx hash and calculate
the source block RID with the correct version (if necessary).
