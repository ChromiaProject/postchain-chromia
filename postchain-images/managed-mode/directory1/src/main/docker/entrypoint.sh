#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

echo "Generating Blockchain Configuration for chain0"
sh multigen.sh "$CHAIN_CONF" --source-dir="$RELL_SRC" --output-dir="$RELL_OUT"

if [ "$WIPE_DB" = true ]; then
  echo "Deleting the database..."
  sh postchain.sh wipe-db --node-config "$RELL_OUT"/node-config.properties

  echo "Adding my peer-info..."
  sh postchain.sh peerinfo-add --node-config "$RELL_OUT"/node-config.properties --host "$NODE_HOST" --port "$NODE_PORT" --pubkey "$NODE_PUBKEY"

  if [ -n "$BOOTSTRAP_NODE_HOST" ]; then
    echo "Adding bootstrap peer-info..."
    sh postchain.sh peerinfo-add --node-config "$RELL_OUT"/node-config.properties --host "$BOOTSTRAP_NODE_HOST" --port "$BOOTSTRAP_NODE_PORT" --pubkey "$BOOTSTRAP_NODE_PUBKEY"
  fi
fi

echo "Starting node"
if [ "${DEBUG:-false}" = true ]; then DEBUG_OPT=--debug; else DEBUG_OPT=''; fi
sh postchain.sh "$1" $DEBUG_OPT --directory "$RELL_OUT"
