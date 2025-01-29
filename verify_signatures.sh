#!/bin/bash

if ! command -v cosign &> /dev/null; then
  echo "Error: cosign is not installed. Please install cosign to use this script."
  exit 1
fi

# Default settings
DIRECTORY_CHAIN_BRID="7E5BE539EF62E48DDA7035867E67734A70833A69D2F162C457282C319AA58AE4"
NODE_HOST="system.chromaway.com:7740"
USE_FALLBACK=false
VALID_EMAILS=()

# Function to display usage instructions
usage() {
  echo "Usage: $0 <image-name> [--directory-chain-brid <brid>] [--node-host <host>] [--fallback-signers] [-h | --help]"
  echo ""
  echo "Options:"
  echo "  <image-name>                    (required) Name of the Docker image to be verified."
  echo "  --directory-chain-brid <brid>   (optional) Directory chain BRID. Default: $DIRECTORY_CHAIN_BRID"
  echo "  --node-host <host>              (optional) Node host name with port. Default: $NODE_HOST"
  echo "  --fallback-signers              (optional) Use fallback signers list if signers cannot be retrieved."
  echo "  -h, --help                      Display this help message."
  exit 0
}

# Check if help flag is provided before any other parameters
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  usage
fi

# Check if the first parameter (image name) is provided
if [ -z "$1" ]; then
  echo "Error: Image name is required"
  usage
fi

IMAGE_NAME="$1"
shift

# Argument parsing
while [[ $# -gt 0 ]]; do
  case "$1" in
    --directory-chain-brid)
      DIRECTORY_CHAIN_BRID="$2"
      shift 2
      ;;
    --node-host)
      NODE_HOST="$2"
      shift 2
      ;;
    --fallback-signers)
      USE_FALLBACK=true
      shift 1
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown parameter: $1"
      usage
      ;;
  esac
done

GET_DEVELOPERS_URL="https://$NODE_HOST/query/$DIRECTORY_CHAIN_BRID?type=get_developers"
API_VERSION_URL="https://$NODE_HOST/query/$DIRECTORY_CHAIN_BRID?type=api_version"

API_VERSION_RESPONSE=$(curl -s -w "|%{http_code}" "$API_VERSION_URL")
API_VERSION=$(echo "$API_VERSION_RESPONSE" | cut -d '|' -f 1)
API_VERSION_RESPONSE_CODE=$(echo "$API_VERSION_RESPONSE" | cut -d '|' -f 2)

FALLBACK_EMAILS=("andrei.ursu@chromaway.com" "johan.nilsson@chromaway.com" "mikael.staldal@chromaway.com" "robert.wideberg@chromaway.com" "eugene.tykulov@chromaway.com")

if [ "$API_VERSION_RESPONSE_CODE" -eq  200 ] && [ "$API_VERSION" -gt 76 ]; then
    RESPONSE=$(curl -s "$GET_DEVELOPERS_URL")
    VALID_EMAILS=($(echo "$RESPONSE" | tr -d '[]"' | tr ',' '\n'))
elif [ "$USE_FALLBACK" = true ]; then
      echo "Using fallback signers list."
      VALID_EMAILS=("${FALLBACK_EMAILS[@]}")
    else
      echo "Error: Could not retrieve developers. Use --fallback-signers to use the fallback signers list."
      exit 1
fi

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
