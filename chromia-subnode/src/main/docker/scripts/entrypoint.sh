#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

echo "Configuring and starting Postgres"
bash postgres-entrypoint.sh postgres

echo "Starting Postchain node"
sh postchain.sh run-server --node-config "$RELL_OUT"/node-config.properties
