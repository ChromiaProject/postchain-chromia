#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")
JAR_FILE=$(find $D/lib -maxdepth 1 -regex '.*postchain-cli-[0-9]*.[0-9]*.[0-9]*.*.jar')
RELL_FILE=$(find $D/lib -maxdepth 1 -regex '.*rell-[0-9]*.[0-9]*.[0-9]*.*.jar')

if [ "${POSTCHAIN_LOG4J2:-}" ]
then
  exec "${RELL_JAVA:-java}" -XX:+CrashOnOutOfMemoryError "-Dlog4j2.configurationFile=${POSTCHAIN_LOG4J2}" -classpath "$JAR_FILE:$RELL_FILE:$D/lib/deps/*" net.postchain.AppKt "$@"
else
  exec "${RELL_JAVA:-java}" -XX:+CrashOnOutOfMemoryError -classpath "$JAR_FILE:$RELL_FILE:$D/lib/deps/*" net.postchain.AppKt "$@"
fi
