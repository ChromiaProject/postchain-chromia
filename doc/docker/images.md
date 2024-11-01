# Docker Image Signing

At Chromaway, developers validate the integrity of published Docker images using the `cosign` tool. The signatures allow users to verify the authenticity of an image and ensure that it has not been tampered with.

## Verifying Docker Image Signatures

Users can either use the `cosign` tool themselves or use the `validate_signatures.sh` script in this project to verify whether a specific Docker image has been signed by authorized Chromaway developers.

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
./validate_signatures.sh <docker-image-name>
```

For example:
```bash
./validate_signatures.sh registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3
```

### Output

- **If the image is signed by signers**:
  ```
  The image 'registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3' is signed.
  Number of signatures: 2
  Valid Signers:
  - andrei.ursu@chromaway.com
  - johan.nilsson@chromaway.com
  ```

- **If the image is not signed by any signers**:
  ```
  The image 'registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.21.3' is NOT signed.
  ```

### Valid Signers

Currently, the following Chromaway developers are authorized to sign Docker images:
- andrei.ursu@chromaway.com
- eugene.tykulov@chromaway.com
- johan.nilsson@chromaway.com
- mikael.staldal@chromaway.com
- robert.wideberg@chromaway.com

