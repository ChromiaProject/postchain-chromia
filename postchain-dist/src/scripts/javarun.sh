#!/bin/bash
set -eu

MAIN_CLASS=${1?"Specify main class!"}
shift

D=`dirname "${BASH_SOURCE[0]}"`

JAR_FILE=$(find $D/lib -maxdepth 1 -regex '.*rell-[0-9]*.[0-9]*.[0-9]*.*.jar')
CP="$JAR_FILE:$D/lib/deps/*"

exec ${RELL_JAVA:-java} -cp "$CP" "$MAIN_CLASS" "$@"
