#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")

export LOG4J_CONFIGURATION_FILE=${LOG4J_CONFIGURATION_FILE:=$D/log4j2.yml}

JVM_FLAGS="-XX:+CrashOnOutOfMemoryError"

exec "${RELL_JAVA:-java}" $JVM_FLAGS -classpath "$D/lib/*" net.postchain.server.AppKt "$@"
