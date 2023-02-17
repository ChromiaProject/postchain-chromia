#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")

JVM_FLAGS="-XX:+CrashOnOutOfMemoryError"

if [ "${POSTCHAIN_LOG4J2:-}" ]
then
  exec "${RELL_JAVA:-java}" $JVM_FLAGS "-Dlog4j2.configurationFile=${POSTCHAIN_LOG4J2}" -classpath "$D/lib/*" net.postchain.AppKt "$@"
else
  exec "${RELL_JAVA:-java}" $JVM_FLAGS -classpath "$D/lib/*" net.postchain.AppKt "$@"
fi
