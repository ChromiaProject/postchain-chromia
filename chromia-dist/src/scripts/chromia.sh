#!/bin/bash
set -eu
D=$(dirname "${BASH_SOURCE[0]}")

JVM_FLAGS="-XX:+CrashOnOutOfMemoryError"

exec "${RELL_JAVA:-java}" $JVM_FLAGS -classpath "$D/lib/*" net.postchain.AppKt "$@"
