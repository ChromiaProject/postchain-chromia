#!/bin/bash

set -euo pipefail

manifest_file="manifest.json"

fetch_gitlab_jwt() {
  local image="$1"
  echo "Fetching GitLab JWT for registry access for $image..."
  GITLAB_JWT=$(curl -s "https://gitlab.com/jwt/auth?scope=repository:chromaway/postchain-chromia/chromaway/$image:pull&service=container_registry" | jq -r .token)

  if [[ -z "$GITLAB_JWT" || "$GITLAB_JWT" == "null" ]]; then
    echo "Error: Unable to fetch GitLab JWT for $image"
    exit 1
  fi
}

checkout_commit() {
  local revision="$1"
  echo "Checking out release tag: $revision"
  git checkout "tags/$revision" || { echo "Failed to checkout tag $revision"; exit 1; }
}

build_docker_images() {
  local revision="$1"
  local platform="$2"
  local docker_image="registry.gitlab.com/chromaway/core-tools/chromia-images/maven-docker-java21:1.0.10@sha256:43b02d847ec3824a0d045f9edc244e6467dde253df4effa01edb1aaabba12d2f"

  local os_type

  os_type=$(uname -s | tr '[:upper:]' '[:lower:]')

  echo "Building Docker image for platform: $platform with revision: $revision on OS: $os_type..."

  if [[ "$os_type" == "darwin" ]]; then
    docker run --rm -it \
      -v "$(pwd)":/postchain-chromia \
      -v "$HOME/.m2:/root/.m2" \
      -w /postchain-chromia \
      "$docker_image" \
      mvn clean package -Djib.from.platforms="linux/$platform" -DskipTests -Drevision="$revision" > build.log 2>&1
  else
    docker run --rm -it \
      -e MAVEN_CONFIG="${HOME}/.m2" \
      --user "$(id -u):$(id -g)" \
      --mount type=bind,source=/etc/passwd,target=/etc/passwd,readonly \
      --mount type=bind,source=/etc/group,target=/etc/group,readonly \
      --mount "type=bind,source=${HOME}/.m2,target=${HOME}/.m2" \
      --mount "type=bind,source=${HOME}/.config/google-cloud-tools-java/jib,target=${HOME}/.config/google-cloud-tools-java/jib,readonly" \
      --mount "type=bind,source=${HOME}/.cache/google-cloud-tools-java/jib,target=${HOME}/.cache/google-cloud-tools-java/jib" \
      --mount "type=bind,source=$(pwd),target=/postchain-chromia" \
      -w /postchain-chromia \
      "$docker_image" \
      mvn clean package -Djib.from.platforms="linux/$platform" -DskipTests -Drevision="$revision" > build.log 2>&1
  fi

  if [[ $? -ne 0 ]]; then
    echo "Failed to build Docker image for platform: $platform with revision: $revision on OS: $os_type."
    echo "Error log:"
    cat build.log
    return 1
  fi

  echo "Docker image successfully built for platform: $platform and revision: $revision on OS: $os_type."
  return 0
}


fetch_full_manifest() {
  local image="$1"
  local revision="$2"

  local manifest_url="https://registry.gitlab.com/v2/chromaway/postchain-chromia/chromaway/$image/manifests/$revision"

  curl -s -H "Accept: application/vnd.docker.distribution.manifest.list.v2+json" \
       -H "Authorization: Bearer $GITLAB_JWT" \
       "$manifest_url" > "$manifest_file"
}

inspect_and_validate_manifest() {
  local image="$1"
  local revision="$2"

  echo "Full manifest for $image (revision $revision):"
  cat "$manifest_file" | jq .

  local computed_digest
  computed_digest="sha256:$(sha256sum "$manifest_file" | awk '{print $1}')"

  local manifest_url="https://registry.gitlab.com/v2/chromaway/postchain-chromia/chromaway/$image/manifests/$revision"
  local remote_digest
  remote_digest=$(curl -s -I -H "Accept: application/vnd.docker.distribution.manifest.list.v2+json" \
                        -H "Authorization: Bearer $GITLAB_JWT" \
                        "$manifest_url" | grep -i "Docker-Content-Digest" | awk '{print $2}' | tr -d '\r')

  echo "Computed Digest: $computed_digest"
  echo "Remote Digest:   $remote_digest"

  if [[ "$computed_digest" != "$remote_digest" ]]; then
    echo "Manifest digest mismatch for $image!"
    exit 1
  fi

  local unsupported_archs
  unsupported_archs=$(jq -r '[.manifests[].platform.architecture | select(. != "amd64" and . != "arm64")] | unique | join(", ")' "$manifest_file")

  if [[ -n "$unsupported_archs" ]]; then
    echo "Error: Manifest for $image contains unsupported architectures: $unsupported_archs"
    exit 1
  fi

  echo "Manifest for $image only contains supported architectures (amd64, arm64)."
  echo "Manifest digest matches for $image."
}

extract_digest_for_platform() {
  local platform="$1"

  cat "$manifest_file" | jq -r --arg platform "$platform" '.manifests[] | select(.platform.architecture == $platform) | .digest'
}

compare_digests() {
  local local_digest="$1"
  local remote_digest="$2"
  local image="$3"
  local platform="$4"

  echo "Comparing local and remote digests for $image on platform $platform..."
  echo "Local Digest:  $local_digest"
  echo "Remote Digest: $remote_digest"

  if [[ "$local_digest" == "$remote_digest" ]]; then
    echo "Digest matches for $image on platform $platform!"
  else
    echo "Digest mismatch for $image on platform $platform."
    exit 1
  fi
}

main() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <revision>"
    exit 1
  fi

  local revision="$1"
  local chromia_server_amd64_digest=""
  local chromia_subnode_amd64_digest=""
  local chromia_server_arm64_digest=""
  local chromia_subnode_arm64_digest=""
  local chromia_server_manifest_digest=""
  local chromia_subnode_manifest_digest=""

  # Step 1: Check out the correct commit
  checkout_commit "$revision"

  # Step 2: Build images and store local digests for amd64
  echo "Building and storing local digests for amd64..."
  build_docker_images "$revision" "amd64"
  chromia_server_amd64_digest=$(cat "./docker-images/chromia-server/target/jib-image.digest")
  chromia_subnode_amd64_digest=$(cat "./docker-images/chromia-subnode/target/jib-image.digest")

  # Step 3: Build images and store local digests for arm64
  echo "Building and storing local digests for arm64..."
  build_docker_images "$revision" "arm64"
  chromia_server_arm64_digest=$(cat "./docker-images/chromia-server/target/jib-image.digest")
  chromia_subnode_arm64_digest=$(cat "./docker-images/chromia-subnode/target/jib-image.digest")

  # Step 4: Validate digests for chromia-server
  echo "Validating chromia-server..."
  fetch_gitlab_jwt "chromia-server"
  fetch_full_manifest "chromia-server" "$revision"
  inspect_and_validate_manifest "chromia-server" "$revision"
  chromia_server_remote_amd64_digest=$(extract_digest_for_platform "amd64")
  chromia_server_remote_arm64_digest=$(extract_digest_for_platform "arm64")
  chromia_server_manifest_digest=$(cat "$manifest_file" | sha256sum | awk '{print $1}')

  compare_digests "$chromia_server_amd64_digest" "$chromia_server_remote_amd64_digest" "chromia-server" "amd64"
  compare_digests "$chromia_server_arm64_digest" "$chromia_server_remote_arm64_digest" "chromia-server" "arm64"

  # Step 5: Validate digests for chromia-subnode
  echo "Validating chromia-subnode..."
  fetch_gitlab_jwt "chromia-subnode"
  fetch_full_manifest "chromia-subnode" "$revision"
  inspect_and_validate_manifest "chromia-subnode" "$revision"
  chromia_subnode_remote_amd64_digest=$(extract_digest_for_platform "amd64")
  chromia_subnode_remote_arm64_digest=$(extract_digest_for_platform "arm64")
  chromia_subnode_manifest_digest=$(cat "$manifest_file" | sha256sum | awk '{print $1}')

  compare_digests "$chromia_subnode_amd64_digest" "$chromia_subnode_remote_amd64_digest" "chromia-subnode" "amd64"
  compare_digests "$chromia_subnode_arm64_digest" "$chromia_subnode_remote_arm64_digest" "chromia-subnode" "arm64"

  echo "Verification successful: chromia-server and chromia-subnode Docker images for all platforms have been verified and are correct!"
  echo "chromia-server  manifest digest: " "$chromia_server_manifest_digest"
  echo "chromia-subnode manifest digest: " "$chromia_subnode_manifest_digest"

  read -p "Do you want to sign the chromia-server image? (y/n): " user_response_server
  if [[ "$user_response_server" =~ ^[Yy]$ ]]; then
    echo "Signing chromia-server image..."
    cosign sign "registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server@sha256:$chromia_server_manifest_digest"
    echo "chromia-server image signed successfully!"
  else
    echo "Skipping signing of chromia-server image."
  fi

  # Prompt the user to sign the chromia-subnode image
  read -p "Do you want to sign the chromia-subnode image? (y/n): " user_response_subnode
  if [[ "$user_response_subnode" =~ ^[Yy]$ ]]; then
    echo "Signing chromia-subnode image..."
    cosign sign "registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode@sha256:$chromia_subnode_manifest_digest"
    echo "chromia-subnode image signed successfully!"
  else
    echo "Skipping signing of chromia-subnode image."
  fi
}

main "$@"