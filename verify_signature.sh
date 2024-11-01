#!/bin/bash

if ! command -v cosign &> /dev/null; then
  echo "Error: cosign is not installed. Please install cosign to use this script."
  exit 1
fi

if [ -z "$1" ]; then
  echo "Usage: $0 <image-name>"
  exit 1
fi

IMAGE_NAME=$1

VALID_EMAILS=("andrei.ursu@chromaway.com" "johan.nilsson@chromaway.com" "mikael.staldal@chromaway.com" "robert.wideberg@chromaway.com" "eugene.tykulov@chromaway.com")
VALID_ISSUER="https://accounts.google.com"

OUTPUT=$(cosign verify --certificate-identity-regexp ".*" --certificate-oidc-issuer-regexp ".*" "$IMAGE_NAME" 2>&1)

SIGNERS=($(echo "$OUTPUT" | grep -Eo '"Subject":"[^"]+"' | sed -E 's/"Subject":"([^"]+)"/\1/'))
ISSUERS=($(echo "$OUTPUT" | grep -Eo '"Issuer":"[^"]+"' | sed -E 's/"Issuer":"([^"]+)"/\1/'))

REAL_SIGNERS=()

for i in "${!SIGNERS[@]}"; do
  SIGNER="${SIGNERS[i]}"
  ISSUER="${ISSUERS[i]}"

  if [[ "$ISSUER" == "$VALID_ISSUER" ]]; then
    for VALID_EMAIL in "${VALID_EMAILS[@]}"; do
      if [[ "$SIGNER" == "$VALID_EMAIL" ]]; then
        REAL_SIGNERS+=("$SIGNER")
        break
      fi
    done
  fi
done

SIGNATURE_COUNT=${#REAL_SIGNERS[@]}

if [ "$SIGNATURE_COUNT" -gt 0 ]; then
  echo "The image '$IMAGE_NAME' is signed."
  echo "Number of signatures: $SIGNATURE_COUNT"
  echo "Signers:"
  for SIGNER in "${REAL_SIGNERS[@]}"; do
    echo "- $SIGNER"
  done
else
    echo "The image '$IMAGE_NAME' is NOT signed."
    exit 1
fi
