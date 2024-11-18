# Docker Image Signing Guide

This guide provides instructions for developers to build Docker images locally, compare their digests with the images built by GitLab, and sign matching images with `cosign`.

## Steps to Follow

### Step 1: Check Out the Correct Commit

Ensure you git checkout the same release tag as the Docker image version you intend to sing.

### Step 2: Start a Docker Container with the Maven Image and build the Docker Image

Using the same base image as specified in `.gitlab-ci.yml` to ensure a consistent build environment, build the image for each required architecture, starting with AMD64.
Note that you must replace `<revision>` with the correct revision number (e.g., `3.21.3`)

```bash
docker run --rm -it \
  -v "$(pwd)":/postchain-chromia \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $HOME/.m2:/root/.m2 \
  -w /postchain-chromia \
  registry.gitlab.com/chromaway/core-tools/chromia-images/maven-docker-java21:1.0.3@sha256:0c19d4c0c62edf299c3f719691df833e197660994a95b66ff3e4bc4756a14d30 \
  mvn clean package -Djib.from.platforms=linux/amd64 -DskipTests -Drevision=<revision>
```

### Step 3: Compare Digests for AMD64

Run the following commands to inspect and compare the image digests. Make sure to replace `<digest>` with the correct digest value (e.g., `sha256:184177f5ddc0cb9894a024fb0953ff37da73345f23896a08502b4221b3fd8d79`).
To retrieve the digest you can use the command: 
```
docker images --digests registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:<revision>
```

1. Inspect the GitLab registry image manifest:

Unfortunately, in order to get a manifest file that you can actually verify you have to use another tool than the docker
client.
It is possible to use tools like `skopeo` and `skopeo inspect` command but below are instructions on how to do it by
interacting directly with the GitLab API:

Get GitLab temporary access token (yes, you have to do this even though images are public):

```bash
GITLAB_JWT=$(curl https://gitlab.com/jwt/auth\?scope\=repository%3Achromaway%2Fpostchain-chromia%2Fchromaway%2Fchromia-server%3Apull\&service\=container_registry | jq -r .token)
```

Fetch the manifest for the digest you would like to verify:

```bash
curl -H "Accept: application/vnd.docker.distribution.manifest.v2+json" -H "Authorization: Bearer $GITLAB_JWT" https://registry.gitlab.com/v2/chromaway/postchain-chromia/chromaway/chromia-server/manifests/sha256:digest > manifest.json
```

Verify that hashing the manifest matches the expected digest:

```bash
sha256sum manifest.json
```

Carefully inspect the manifest to verify that it only contains two builds, one for `amd64` and one for `arm64`.
These are the ones we will verify by running the build locally.
To make this a bit easier you can pretty-print the manifest file:

```bash
jq . manifest.json
```

   Example output:
   ```json
   {
      "schemaVersion": 2,
      "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json",
      "manifests": [
         {
            "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "size": 1563,
            "digest": "sha256:de12d685fe5a6b4c42d1eeb8d2cdb1de9e70047c07837a4f38366b71312994d9",
            "platform": {
               "architecture": "amd64",
               "os": "linux"
            }
         },
         {
            "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "size": 1563,
            "digest": "sha256:4efd7dbb85ffd410959fcee29f9a5edda18527ba9b3ba6a8f053511819ea0ddf",
            "platform": {
               "architecture": "arm64",
               "os": "linux"
            }
         }
      ]
   }
   ```

2. Check the locally built image digest:
   ```bash
   cat ./docker-images/chromia-server/target/jib-image.digest
   ```

Example output:
  ```
  sha256:de12d685fe5a6b4c42d1eeb8d2cdb1de9e70047c07837a4f38366b71312994d9
  ```

Compare the output of both commands.
If the `jib-image.digest` matches the GitLab digest for amd64 architecture, proceed to Step 4.
If there’s a mismatch, tools like `container-diff` or `dive` can help diagnose image differences.

### Step 4: Repeat Step 2 & 3 for the ARM64 Image

Follow the same process as above, but build for the ARM64 architecture using `-Djib.from.platforms=linux/arm64` option in the `mvn` command.

### Step 5: Sign the Image if Digests Match

If the digests match, sign the image in the GitLab registry with `cosign`:
```bash
cosign sign registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server@<digest>
```

### Step 6: Repeat process for chromia-subnode

The above steps can be used to verify and sign the chromia-subnode image.

## Verifying Docker Image Signatures

The signatures allow users to verify the authenticity of an image and ensure that it has not been tampered with.
Users can either use the `cosign` tool themselves or use the `verify_signatures.sh` script in this project to verify whether a specific Docker image has been signed by authorized Chromaway developers.

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

### Valid Signers

Currently, the following Chromaway developers are authorized to sign Docker images:
- andrei.ursu@chromaway.com
- eugene.tykulov@chromaway.com
- johan.nilsson@chromaway.com
- mikael.staldal@chromaway.com
- robert.wideberg@chromaway.com

