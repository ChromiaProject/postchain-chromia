#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

# Percentage of total memory to dedicate to java/psql (30% buffer with current setup)
JAVA_MEMORY_SHARE=35
export PSQL_MEMORY_SHARE=35

echo "Configuring and starting Postgres"
bash postgres-entrypoint.sh postgres

echo "Starting Postchain node"
exec java -XX:+CrashOnOutOfMemoryError -XX:MaxRAMPercentage=$JAVA_MEMORY_SHARE -classpath "$POSTCHAIN_DIR/lib/*" net.postchain.AppKt run-subnode