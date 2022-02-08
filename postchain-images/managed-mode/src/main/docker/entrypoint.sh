#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu

echo "Generating Blockchain Configuration for chain0"
sh multigen.sh --source-dir=$RELL_SRC --output-dir=$RELL_OUT $RELL_CONF

if [ $WIPE_DB = true ]; then
  echo "Deleting the database..."
  sh postchain.sh wipe-db --node-config $RELL_OUT/node-config.properties

  echo "Adding my peer-info..."
  sh postchain.sh peerinfo-add --node-config $RELL_OUT/node-config.properties --host $NODE_HOST --port $NODE_PORT --pub-key $NODE_PUBKEY
fi

echo "Starting node"
sh postchain.sh $1 -d $RELL_OUT
