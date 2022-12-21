#!/bin/bash
set -eu

scriptdir=`dirname ${BASH_SOURCE[0]}`

${RELL_JAVA:-java} -jar $scriptdir/lib/rellr.jar $@
