#!/bin/bash

set -eu

scriptdir=$(dirname "${BASH_SOURCE[0]}")

${RELL_JAVA:-java} -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1044 -cp "$scriptdir/postchain-node/lib/*" net.postchain.AppKt "$@"
# ${RELL_JAVA:-java}  -cp "$scriptdir/lib/*" net.postchain.AppKt $@
