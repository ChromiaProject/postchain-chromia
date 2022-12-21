#!/bin/sh

set -eu

java -classpath "$POSTCHAIN_DIR/lib/*" net.postchain.rell.tools.runcfg.RellRunConfigLaunchKt "$@"
