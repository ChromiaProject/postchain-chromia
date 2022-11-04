#!/bin/bash

set -eu

SCRIPT_DIR=$(dirname "$(dirname "${BASH_SOURCE[0]}")")

${RELL_JAVA:-java} -cp "$SCRIPT_DIR/lib/*" net.postchain.deployment.cli.DeployToolKt "$@"
