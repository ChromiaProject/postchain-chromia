#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")

if [ "${POSTCHAIN_LOG_CONFIG_FILE:-}" ]
then
  exec "${RELL_JAVA:-java}" "-Dlog4j2.configurationFile=${POSTCHAIN_LOG_CONFIG_FILE}" -classpath "$D/lib/*" net.postchain.AppKt "$@"
else
  exec "${RELL_JAVA:-java}" -classpath "$D/lib/*" net.postchain.AppKt "$@"
fi
