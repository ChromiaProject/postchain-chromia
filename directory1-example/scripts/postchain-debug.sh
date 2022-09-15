#!/bin/bash

set -eu

bash "$POSTCHAIN_DIR"/postchain.sh "$@" -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1044
