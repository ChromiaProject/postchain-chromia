#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

echo "Configuring and starting Postgres"
bash postgres-entrypoint.sh postgres

echo "Starting Postchain node"
exec java -XX:+CrashOnOutOfMemoryError -Dlog4j2.configurationFile="$POSTCHAIN_LOG4J2" -classpath "$POSTCHAIN_DIR/lib/*" net.postchain.AppKt run-server
