#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")
JAR_FILE=$(find $D/lib -maxdepth 1 -regex '.*postchain-cli-[0-9]*.[0-9]*.[0-9]*.*.jar')
RELL_FILE=$(find $D/lib -maxdepth 1 -regex '.*rell-[0-9]*.[0-9]*.[0-9]*.*.jar')

exec "${RELL_JAVA:-java}" -classpath "$JAR_FILE:$RELL_FILE:$D/lib/deps/*" net.postchain.AppKt "$@"
