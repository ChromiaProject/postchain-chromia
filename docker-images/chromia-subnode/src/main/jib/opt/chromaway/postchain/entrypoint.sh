#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

# Percentage of total memory to dedicate to java/psql (30% buffer with current setup)
JAVA_MEMORY_SHARE=35
export PSQL_MEMORY_SHARE=35
export ALREADY_INITED_FILE=".db-initialized"
MIGRATE_POSTGRES=${MIGRATE_POSTGRES:-false}

# Exit container if run as root
if [ "$(id -u)" = '0' ]; then
  echo "Do not run this container as root"
  exit 1
fi

trap 'echo "Shutting down..." ; kill ${POSTCHAIN_PID} ; pg_ctl stop -m smart' TERM INT

echo "Configuring and starting Postgres"
if [ "$MIGRATE_POSTGRES" = "true" ] && [ -f "$PGDATA/$ALREADY_INITED_FILE" ]; then
  bash postgres-migrate.sh
  if [ -z "$(ls -A $PGDATA)" ]; then
    echo "No data found in PGDATA - this is normal for a successful migration, exiting container to have it restarted"
    exit 1
  fi
fi
bash postgres-entrypoint.sh postgres

java -Duser.language=en -Duser.country=US -XX:+UnlockDiagnosticVMOptions -XX:AbortVMOnException=java.lang.OutOfMemoryError -XX:MaxRAMPercentage=$JAVA_MEMORY_SHARE -classpath "$POSTCHAIN_DIR/libs/*:$POSTCHAIN_DIR/classpath/*" net.postchain.server.AppKt run-subnode &
POSTCHAIN_PID="$!"
echo "Started Postchain node with PID ${POSTCHAIN_PID}"
wait ${POSTCHAIN_PID}
