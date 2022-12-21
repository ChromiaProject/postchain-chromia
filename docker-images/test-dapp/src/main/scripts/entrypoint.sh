#!/usr/bin/env bash

set -eu


if [ "$1" = "update" ] || [ "$GENERATE_BLOCKCHAIN" = true ]; then
  echo "Generating Blockchain Configuration"
  sh ./multigen.sh --source-dir="$RELL_SRC" --output-dir="$RELL_OUT" "$CHAIN_CONF"
fi

if [ "$WIPE_DB" = true ]  && [ "$1" != "update" ]; then
  echo "Deleting the database..."
  ./postchain.sh wipe-db -nc "$NODE_CONF"
fi

if [ "$1" = "test" ]
then
  echo "Running tests"
  sh ./multirun.sh --source-dir "$RELL_SRC" --test "$CHAIN_CONF"
elif [ "$1" = "single-test" ]
then
  echo "Running Single Test: $2"
  sh ./multirun.sh --source-dir "$RELL_SRC" --test "$CHAIN_CONF" --test-filter "$2"
elif [ "$1" = "update" ]
then
  CHAIN_ID=$(xmllint --xpath 'string(//run/chains/chain/@iid)' "$CHAIN_CONF")
  echo "Updating blockchain configuration for chain $CHAIN_ID"
  sh ./postchain.sh add-configuration --allow-unknown-signers \
   --node-config "$NODE_CONF" \
   --chain-id "$CHAIN_ID" \
   --blockchain-config "$RELL_OUT/blockchains/$CHAIN_ID/0.xml" \
   --future-height 5
else
  echo "Starting node"
  sh ./postchain.sh "$1" -d "$RELL_OUT"
fi
