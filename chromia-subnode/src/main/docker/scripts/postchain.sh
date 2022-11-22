#!/bin/sh

set -eu

java -Dlog4j.configurationFile="$POSTCHAIN_LOG4J2" -classpath "$POSTCHAIN_DIR/lib/*" net.postchain.AppKt $@

