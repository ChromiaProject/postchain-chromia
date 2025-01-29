# Docker Image Signing Guide

This guide provides instructions for developers to build Docker images locally, compare their digests with the images built by GitLab, and sign matching images with `cosign`.

## Verify and Sign Images

`verify_and_sign_images.sh` ensures that the Docker images `chromia-server` and `chromia-subnode` are built correctly, their digests match the registry's digests, and the manifest is valid for supported architectures (amd64 and arm64) before signing the images.

### Requirements

Before using the script, make sure `jq` and `cosign` are installed, and you have access to the GitLab container registry.

### Example Usage

```bash
./verify_and_sign_images.sh 3.22.3
```

## Verifying Docker Image Signatures

The signatures allow users to verify the authenticity of an image and ensure that it has not been tampered with. Users can either use the `cosign` tool themselves or use the `verify_signatures.sh` script in this project to verify whether a specific Docker image has been signed by authorized Chromaway developers.

### Requirements

Before using the script, make sure `cosign` is installed. You can install it via:

- **Homebrew** (macOS/Linux):

  ```bash
  brew install sigstore/tap/cosign
  ```

- **Manual Installation**:
  Follow instructions on the [cosign GitHub page](https://github.com/sigstore/cosign#installation).

### Usage

To verify a Docker image, run the following command:

```bash
./verify_signatures.sh <docker-image-name>
```

For example:

```bash
./verify_signatures.sh registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3
```

You can also use the digest:

```bash
./verify_signatures.sh registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server@sha256:184177f5ddc0cb9894a024fb0953ff37da73345f23896a08502b4221b3fd8d79
```

### Developer Emails for Signature Verification

By default, the script retrieves the list of authorized developer emails from a **directory chain** on a configured Chromaway node.

- **Default Configuration**: The script queries the Chromaway node `system.chromaway.com:7740` and retrieves developer emails from the directory chain BRID `7E5BE539EF62E48DDA7035867E67734A70833A69D2F162C457282C319AA58AE4`.
- **Custom Configuration**: You can specify a different **node host** and **directory chain BRID** using the following parameters:
  ```bash
  ./verify_signatures.sh <docker-image-name> --node-host my-custom-node.com:7740 --directory-chain-brid MY_CUSTOM_BRID
  ```

### Fallback Option

If the configured node is **unreachable**, **returns an error**, or **has a low software version**, the script allows you to use a **fallback signer list** instead.

To enable the fallback option, use the `--fallback-signers` flag:

```bash
./verify_signatures.sh <docker-image-name> --fallback-signers
```

The fallback signers list includes:

- [andrei.ursu@chromaway.com](mailto\:andrei.ursu@chromaway.com)
- [eugene.tykulov@chromaway.com](mailto\:eugene.tykulov@chromaway.com)
- [johan.nilsson@chromaway.com](mailto\:johan.nilsson@chromaway.com)
- [mikael.staldal@chromaway.com](mailto\:mikael.staldal@chromaway.com)
- [robert.wideberg@chromaway.com](mailto\:robert.wideberg@chromaway.com)

### Output

- **If the image is signed**:

  ```
  The image 'registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3' is signed.
  Number of signatures: 2
  Valid Signers:
  - andrei.ursu@chromaway.com
  - johan.nilsson@chromaway.com
  ```

- **If the image is not signed**:

  ```
  The image 'registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3' is NOT signed.
  ```

